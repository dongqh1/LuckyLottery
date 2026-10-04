package com.dongqh.lotteryservice.draworder.service;

import com.dongqh.lotteryservice.config.LotteryProperties;
import com.dongqh.lotteryservice.domain.LotteryDomainException;
import com.dongqh.lotteryservice.drawchance.enums.DrawChanceReservationStatus;
import com.dongqh.lotteryservice.drawchance.model.DrawChanceReservationCommand;
import com.dongqh.lotteryservice.drawchance.model.DrawChanceReservationResult;
import com.dongqh.lotteryservice.drawchance.service.DrawChanceReservationService;
import com.dongqh.lotteryservice.draweligibility.DrawEligibilityService;
import com.dongqh.lotteryservice.draweligibility.EligibilitySnapshot;
import com.dongqh.lotteryservice.draworder.dto.DrawCommand;
import com.dongqh.lotteryservice.draworder.entity.DrawOrderRow;
import com.dongqh.lotteryservice.draworder.enums.DrawOrderStatus;
import com.dongqh.lotteryservice.draworder.mapper.DrawOrderMapper;
import com.dongqh.lotteryservice.draworder.model.NewDrawOrder;
import com.dongqh.lotteryservice.draworder.vo.DrawOrderView;
import com.dongqh.lotteryservice.draworder.vo.DrawResultView;
import com.dongqh.lotteryservice.drawtransaction.entity.DrawRecordRow;
import com.dongqh.lotteryservice.drawtransaction.enums.DrawResultType;
import com.dongqh.lotteryservice.drawtransaction.mapper.DrawRecordMapper;
import com.dongqh.lotteryservice.drawtransaction.model.DrawExecutionContext;
import com.dongqh.lotteryservice.drawtransaction.model.DrawExecutionResult;
import com.dongqh.lotteryservice.drawtransaction.model.DrawResultItem;
import com.dongqh.lotteryservice.drawtransaction.model.RewardSnapshot;
import com.dongqh.lotteryservice.drawtransaction.service.DrawTransactionService;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import com.dongqh.lotteryservice.lock.DrawLockService;
import com.dongqh.lotteryservice.prizecatalog.domain.RewardType;
import com.dongqh.lotteryservice.quota.DrawQuotaService;
import com.dongqh.lotteryservice.quota.QuotaReservationRequest;
import com.dongqh.lotteryservice.quota.QuotaReservationResult;
import com.dongqh.lotteryservice.quota.ReservationStatus;
import com.hmall.common.utils.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class LotteryDrawService {
    private static final Logger log = LoggerFactory.getLogger(LotteryDrawService.class);

    private final DrawOrderMapper orders;
    private final DrawRecordMapper records;
    private final DrawEligibilityService eligibility;
    private final DrawLockService locks;
    private final DrawQuotaService quotas;
    private final DrawOrderLifecycleService lifecycle;
    private final DrawTransactionService transactions;
    private final DrawChanceReservationService drawChances;

    public LotteryDrawService(DrawOrderMapper orders, DrawRecordMapper records,
                              DrawEligibilityService eligibility, DrawLockService locks,
                              DrawQuotaService quotas, DrawOrderLifecycleService lifecycle,
                              DrawTransactionService transactions, DrawChanceReservationService drawChances) {
        this.orders = orders;
        this.records = records;
        this.eligibility = eligibility;
        this.locks = locks;
        this.quotas = quotas;
        this.lifecycle = lifecycle;
        this.transactions = transactions;
        this.drawChances = drawChances;
    }

    public DrawOrderView draw(DrawCommand command) {
        validate(command);
        Long contextUserId = UserContext.getUser();
        if (contextUserId == null || contextUserId <= 0) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_ACCESS_DENIED);
        }
        long userId = contextUserId;
        DrawOrderRow existing = orders.selectByRequestId(command.requestId());
        if (existing != null) return resolveExisting(existing, command, userId);

        EligibilitySnapshot snapshot = eligibility.load(command.activityId());
        LocalDate drawDate = snapshot.snapshotTime().toLocalDate();
        DrawOrderRow[] createdOrder = new DrawOrderRow[1];
        try {
            LockedDraw locked = locks.execute(command.activityId(), userId, () -> {
                DrawOrderRow second = orders.selectByRequestId(command.requestId());
                if (second != null) return new LockedDraw(resolveExisting(second, command, userId), null, null);

                DrawChanceReservationCommand chanceCommand = new DrawChanceReservationCommand(
                        command.requestId(), command.activityId(), userId, command.drawCount(), drawDate);
                DrawChanceReservationResult bonus = drawChances.reserve(chanceCommand);
                requireActiveChance(bonus);

                QuotaReservationResult quota;
                try {
                    long totalLimit = Math.addExact((long) snapshot.dailyLimit(), bonus.cumulativeBonusForDate());
                    quota = quotas.reserve(new QuotaReservationRequest(command.requestId(), command.activityId(),
                            userId, command.drawCount(), totalLimit, drawDate));
                    requireActiveQuota(quota);
                } catch (RuntimeException error) {
                    drawChances.release(command.requestId());
                    throw error;
                }

                DrawOrderRow processing;
                try {
                    processing = lifecycle.createProcessing(new NewDrawOrder(command.requestId(), userId,
                            command.activityId(), command.drawCount(), quota.drawDate()));
                } catch (RuntimeException error) {
                    quotas.release(command.requestId());
                    drawChances.release(command.requestId());
                    throw error;
                }
                createdOrder[0] = processing;
                requireActiveChance(drawChances.reserve(chanceCommand));
                return new LockedDraw(null, processing, quota);
            });

            if (locked.existingView() != null) return locked.existingView();
            DrawOrderRow order = locked.order();
            DrawExecutionResult result = transactions.execute(new DrawExecutionContext(order.getId(),
                    command.requestId(), userId, command.activityId(), command.drawCount(),
                    locked.reservation().drawDate(), snapshot.noWinWeight(), snapshot.prizes(),
                    snapshot.snapshotTime()));
            return fromExecution(order, result);
        } catch (LotteryDomainException error) {
            compensateIfNeeded(createdOrder[0], snapshot.snapshotTime());
            throw error;
        } catch (RuntimeException error) {
            compensateIfNeeded(createdOrder[0], snapshot.snapshotTime());
            throw new LotteryDomainException(LotteryErrorCode.DRAW_TRANSACTION_FAILED);
        }
    }

    public DrawOrderView getByRequestId(String requestId) {
        validateRequestId(requestId);
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) throw new LotteryDomainException(LotteryErrorCode.DRAW_ACCESS_DENIED);
        DrawOrderRow order = orders.selectByRequestId(requestId);
        if (order == null) throw new LotteryDomainException(LotteryErrorCode.DRAW_ORDER_FAILED);
        if (!userId.equals(order.getUserId())) throw new LotteryDomainException(LotteryErrorCode.DRAW_ACCESS_DENIED);
        return toStoredView(order);
    }

    private DrawOrderView resolveExisting(DrawOrderRow order, DrawCommand command, long userId) {
        if (!Long.valueOf(userId).equals(order.getUserId())) {
            throw new LotteryDomainException(LotteryErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (!command.activityId().equals(order.getActivityId())
                || !command.drawCount().equals(order.getDrawCount())) {
            throw new LotteryDomainException(LotteryErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (order.getStatus() == DrawOrderStatus.PROCESSING) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_ORDER_PROCESSING);
        }
        if (order.getStatus() == DrawOrderStatus.FAILED) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_ORDER_FAILED);
        }
        return toStoredView(order);
    }

    private DrawOrderView toStoredView(DrawOrderRow order) {
        List<DrawResultView> resultViews = order.getStatus() == DrawOrderStatus.SUCCESS
                ? records.selectByOrderId(order.getId()).stream().map(this::toView).toList() : List.of();
        return new DrawOrderView(order.getId(), order.getRequestId(), order.getActivityId(), order.getDrawCount(),
                order.getDrawDate(), order.getStatus(), order.getFailReason(), order.getCompletedAt(), resultViews);
    }

    private DrawOrderView fromExecution(DrawOrderRow order, DrawExecutionResult result) {
        List<DrawResultView> items = result.items().stream().map(this::toView).toList();
        return new DrawOrderView(order.getId(), order.getRequestId(), order.getActivityId(), order.getDrawCount(),
                order.getDrawDate(), result.status(), null, result.completedAt(), items);
    }

    private DrawResultView toView(DrawResultItem item) {
        RewardSnapshot reward = item.rewardSnapshot();
        Long itemId = reward != null && reward.rewardType() == RewardType.PRODUCT ? reward.targetId() : null;
        return new DrawResultView(item.recordId(), item.sequenceNo(), item.resultType(), item.prizeId(),
                item.prizeName(), item.prizeType(), item.prizeImageUrl(), item.benefitId(), itemId);
    }

    private DrawResultView toView(DrawRecordRow row) {
        Long itemId = row.getRewardType() == RewardType.PRODUCT ? row.getRewardTargetId() : null;
        return new DrawResultView(row.getId(), row.getSequenceNo(), row.getResultType(), row.getPrizeId(),
                row.getPrizeName(), row.getPrizeType(), row.getPrizeImageUrl(), row.getBenefitId(), itemId);
    }

    private void compensateIfNeeded(DrawOrderRow order, LocalDateTime occurredAt) {
        if (order == null) return;
        try {
            lifecycle.markFailedAndRequestRelease(order, "DRAW_TRANSACTION_FAILED", occurredAt);
        } catch (RuntimeException compensationError) {
            log.error("Failed to compensate lottery draw, requestId={}, orderId={}",
                    order.getRequestId(), order.getId(), compensationError);
        }
    }

    private void requireActiveChance(DrawChanceReservationResult reservation) {
        if (reservation == null || reservation.status() != DrawChanceReservationStatus.RESERVED) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_ORDER_FAILED);
        }
    }

    private void requireActiveQuota(QuotaReservationResult reservation) {
        if (reservation == null || reservation.status() == null) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_QUOTA_UNAVAILABLE);
        }
        if (reservation.status() != ReservationStatus.RESERVED) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_ORDER_FAILED);
        }
    }

    private void validate(DrawCommand command) {
        if (command == null || command.activityId() == null || command.activityId() <= 0
                || command.drawCount() == null || (command.drawCount() != 1 && command.drawCount() != 10)) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
        validateRequestId(command.requestId());
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
        try {
            if (!UUID.fromString(requestId).toString().equalsIgnoreCase(requestId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_PARAMETER_INVALID);
        }
    }

    private record LockedDraw(DrawOrderView existingView, DrawOrderRow order,
                              QuotaReservationResult reservation) {
    }
}
