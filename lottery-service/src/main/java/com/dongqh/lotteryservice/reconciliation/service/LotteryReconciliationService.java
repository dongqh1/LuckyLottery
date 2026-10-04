package com.dongqh.lotteryservice.reconciliation.service;

import com.dongqh.lotteryservice.config.LotteryProperties;
import com.dongqh.lotteryservice.drawchance.service.DrawChanceReservationService;
import com.dongqh.lotteryservice.draworder.entity.DrawOrderRow;
import com.dongqh.lotteryservice.draworder.enums.DrawOrderStatus;
import com.dongqh.lotteryservice.draworder.mapper.DrawOrderMapper;
import com.dongqh.lotteryservice.draworder.service.DrawOrderLifecycleService;
import com.dongqh.lotteryservice.lock.DrawLockService;
import com.dongqh.lotteryservice.quota.DrawQuotaKeys;
import com.dongqh.lotteryservice.quota.DrawQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class LotteryReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(LotteryReconciliationService.class);
    private static final String PROCESSING_TIMEOUT = "PROCESSING_TIMEOUT";
    private static final DateTimeFormatter DRAW_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final List<Object> RESERVATION_FIELDS = List.of(
            "requestId", "activityId", "userId", "drawCount", "drawDate", "status", "createdAt");

    private final StringRedisTemplate redis;
    private final DrawOrderMapper orders;
    private final DrawOrderLifecycleService lifecycle;
    private final DrawQuotaService quota;
    private final DrawLockService locks;
    private final DrawChanceReservationService drawChances;
    private final Duration processingTimeout;
    private final ZoneId zoneId;
    private final int batchSize;

    public LotteryReconciliationService(StringRedisTemplate redis, DrawOrderMapper orders,
                                        DrawOrderLifecycleService lifecycle, DrawQuotaService quota,
                                        DrawLockService locks, DrawChanceReservationService drawChances,
                                        LotteryProperties properties) {
        this.redis = Objects.requireNonNull(redis);
        this.orders = Objects.requireNonNull(orders);
        this.lifecycle = Objects.requireNonNull(lifecycle);
        this.quota = Objects.requireNonNull(quota);
        this.locks = Objects.requireNonNull(locks);
        this.drawChances = Objects.requireNonNull(drawChances);
        this.processingTimeout = Objects.requireNonNull(properties.processingTimeout());
        this.zoneId = Objects.requireNonNull(properties.zoneId());
        this.batchSize = properties.reconcileBatchSize();
    }

    public ReconciliationSummary reconcile(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        Set<String> due = redis.opsForZSet().rangeByScore(
                DrawQuotaKeys.reservationTimeouts(), 0, now.toEpochMilli(), 0, batchSize);
        if (due == null) due = Collections.emptySet();

        MutableSummary result = new MutableSummary(due.size());
        for (String requestId : due) {
            try {
                reconcileOne(requestId, now, result);
            } catch (RuntimeException error) {
                result.failed++;
                log.warn("Lottery reservation reconciliation failed, requestId={}", requestId, error);
                defer(requestId, now);
            }
        }
        int settledDrawChances = reconcileDrawChances(now);
        return result.snapshot(settledDrawChances);
    }

    private int reconcileDrawChances(Instant now) {
        LocalDateTime cutoff = LocalDateTime.ofInstant(now, zoneId).minus(processingTimeout);
        return drawChances.reconcileExpired(batchSize, cutoff);
    }

    private void reconcileOne(String requestId, Instant now, MutableSummary result) {
        ReservationSnapshot initial = readReservationOrCleanTerminal(requestId);
        if (initial == null) return;
        locks.execute(initial.activityId(), initial.userId(), () -> {
            reconcileUnderDrawLock(requestId, initial, now, result);
            return null;
        });
    }

    private void reconcileUnderDrawLock(String requestId, ReservationSnapshot initial,
                                        Instant now, MutableSummary result) {
        ReservationSnapshot reservation = readReservationOrCleanTerminal(requestId);
        if (reservation == null) return;
        if (!reservation.sameIdentity(initial)) {
            throw new IllegalStateException("Reservation identity changed while acquiring draw lock");
        }
        if (reservation.status() == ReservationState.CONFIRMED) {
            quota.confirm(requestId);
            result.confirmed++;
            return;
        }
        if (reservation.status() == ReservationState.RELEASED) {
            quota.release(requestId);
            result.released++;
            return;
        }

        DrawOrderRow order = orders.selectByRequestId(requestId);
        if (order == null) {
            quota.release(requestId);
            result.released++;
            return;
        }
        validateOrderIdentity(order, reservation);
        if (order.getStatus() == DrawOrderStatus.FAILED) {
            quota.release(requestId);
            result.released++;
            return;
        }
        if (order.getStatus() == DrawOrderStatus.SUCCESS) {
            quota.confirm(requestId);
            result.confirmed++;
            return;
        }
        if (order.getStatus() != DrawOrderStatus.PROCESSING) {
            throw new IllegalStateException("Unsupported draw order status: " + order.getStatus());
        }

        Instant deadline = deadline(order);
        if (deadline.isAfter(now)) {
            redis.opsForZSet().add(DrawQuotaKeys.reservationTimeouts(), requestId, deadline.toEpochMilli());
            result.deferred++;
            return;
        }

        lifecycle.markFailedAndRequestRelease(order, PROCESSING_TIMEOUT, LocalDateTime.ofInstant(now, zoneId));
        DrawOrderRow finalOrder = orders.selectByRequestId(requestId);
        if (finalOrder == null) {
            quota.release(requestId);
            result.released++;
            return;
        }
        validateOrderIdentity(finalOrder, reservation);
        if (finalOrder.getStatus() == DrawOrderStatus.SUCCESS) {
            quota.confirm(requestId);
            result.confirmed++;
        } else if (finalOrder.getStatus() == DrawOrderStatus.FAILED
                && PROCESSING_TIMEOUT.equals(finalOrder.getFailReason())) {
            // The order transition and release event commit together. The timeout entry
            // stays as a fallback until the outbox consumer removes it from Redis.
            result.timedOut++;
        } else if (finalOrder.getStatus() == DrawOrderStatus.FAILED) {
            quota.release(requestId);
            result.released++;
        } else {
            result.deferred++;
        }
    }

    private Instant deadline(DrawOrderRow order) {
        if (order.getCreatedAt() == null) {
            throw new IllegalStateException("PROCESSING order has no createdAt");
        }
        return order.getCreatedAt().atZone(zoneId).toInstant().plus(processingTimeout);
    }

    private ReservationSnapshot readReservationOrCleanTerminal(String requestId) {
        List<Object> values = redis.opsForHash().multiGet(
                DrawQuotaKeys.reservation(requestId), RESERVATION_FIELDS);
        String rawStatus = value(values, 5);
        if (values == null || values.stream().allMatch(Objects::isNull)) {
            quota.removeTimeout(requestId);
            return null;
        }
        try {
            String storedRequestId = required(value(values, 0), "requestId");
            if (!requestId.equals(storedRequestId) || storedRequestId.length() > 64) {
                throw new IllegalStateException("reservation requestId mismatch");
            }
            long activityId = positiveLong(value(values, 1), "activityId");
            long userId = positiveLong(value(values, 2), "userId");
            int drawCount = Integer.parseInt(required(value(values, 3), "drawCount"));
            if (drawCount != 1 && drawCount != 10) {
                throw new IllegalStateException("invalid reservation drawCount");
            }
            LocalDate drawDate = LocalDate.parse(required(value(values, 4), "drawDate"), DRAW_DATE);
            ReservationState status = ReservationState.valueOf(required(rawStatus, "status"));
            long createdAt = Long.parseLong(required(value(values, 6), "createdAt"));
            if (createdAt <= 0) throw new IllegalStateException("invalid reservation createdAt");
            return new ReservationSnapshot(storedRequestId, activityId, userId, drawCount,
                    drawDate, status, createdAt);
        } catch (RuntimeException invalid) {
            if ("CONFIRMED".equals(rawStatus) || "RELEASED".equals(rawStatus)) {
                quota.removeTimeout(requestId);
                return null;
            }
            throw new IllegalStateException("Invalid RESERVED reservation identity", invalid);
        }
    }

    private void validateOrderIdentity(DrawOrderRow order, ReservationSnapshot reservation) {
        if (!Objects.equals(order.getRequestId(), reservation.requestId())
                || !Objects.equals(order.getActivityId(), reservation.activityId())
                || !Objects.equals(order.getUserId(), reservation.userId())
                || !Objects.equals(order.getDrawCount(), reservation.drawCount())
                || !Objects.equals(order.getDrawDate(), reservation.drawDate())) {
            throw new IllegalStateException("Reservation and draw order identity mismatch");
        }
    }

    private void defer(String requestId, Instant now) {
        try {
            redis.opsForZSet().add(DrawQuotaKeys.reservationTimeouts(), requestId,
                    now.plus(processingTimeout).toEpochMilli());
        } catch (RuntimeException rescheduleError) {
            log.warn("Failed to defer lottery reconciliation retry, requestId={}", requestId, rescheduleError);
        }
    }

    private static String value(List<Object> values, int index) {
        if (values == null || values.size() <= index || values.get(index) == null) return null;
        return values.get(index).toString();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is missing");
        return value;
    }

    private static long positiveLong(String value, String name) {
        long parsed = Long.parseLong(required(value, name));
        if (parsed <= 0) throw new IllegalStateException(name + " must be positive");
        return parsed;
    }

    private enum ReservationState { RESERVED, CONFIRMED, RELEASED }

    private record ReservationSnapshot(String requestId, long activityId, long userId,
                                       int drawCount, LocalDate drawDate,
                                       ReservationState status, long createdAt) {
        private boolean sameIdentity(ReservationSnapshot other) {
            return requestId.equals(other.requestId()) && activityId == other.activityId()
                    && userId == other.userId() && drawCount == other.drawCount()
                    && drawDate.equals(other.drawDate());
        }
    }

    public record ReconciliationSummary(int scanned, int confirmed, int released, int timedOut,
                                         int deferred, int failed, int settledDrawChances) {
    }

    private static final class MutableSummary {
        private final int scanned;
        private int confirmed;
        private int released;
        private int timedOut;
        private int deferred;
        private int failed;

        private MutableSummary(int scanned) {
            this.scanned = scanned;
        }

        private ReconciliationSummary snapshot(int settledDrawChances) {
            return new ReconciliationSummary(scanned, confirmed, released, timedOut,
                    deferred, failed, settledDrawChances);
        }
    }
}
