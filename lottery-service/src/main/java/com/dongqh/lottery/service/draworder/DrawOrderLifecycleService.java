package com.dongqh.lottery.service.draworder;

import com.dongqh.lottery.domain.po.draworder.DrawOrderRow;
import com.dongqh.lottery.enums.draworder.DrawOrderStatus;
import com.dongqh.lottery.mapper.draworder.DrawOrderMapper;
import com.dongqh.lottery.domain.model.draworder.NewDrawOrder;
import com.dongqh.lottery.outbox.event.DrawEventEnvelope;
import com.dongqh.lottery.outbox.event.DrawEventType;
import com.dongqh.lottery.outbox.event.DrawReleaseRequestedEvent;
import com.dongqh.lottery.outbox.service.OutboxService;
import com.dongqh.lottery.exception.LotteryDomainException;
import com.dongqh.lottery.enums.LotteryErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class DrawOrderLifecycleService {
    private static final int MAX_REASON_LENGTH = 500;
    private static final String DEFAULT_SAFE_REASON = "DRAW_TRANSACTION_FAILED";

    private final DrawOrderMapper orderMapper;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public DrawOrderLifecycleService(DrawOrderMapper orderMapper, OutboxService outboxService,
                                     ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DrawOrderRow createProcessing(NewDrawOrder command) {
        Objects.requireNonNull(command, "command must not be null");
        DrawOrderRow existing = orderMapper.selectByRequestId(command.requestId());
        if (existing != null) {
            validateIdentity(existing, command);
            return existing;
        }

        DrawOrderRow order = new DrawOrderRow();
        order.setRequestId(command.requestId());
        order.setUserId(command.userId());
        order.setActivityId(command.activityId());
        order.setDrawCount(command.drawCount());
        order.setDrawDate(command.drawDate());
        order.setStatus(DrawOrderStatus.PROCESSING);
        orderMapper.insertProcessingIfAbsent(order);

        DrawOrderRow persisted = orderMapper.selectByRequestIdForUpdate(command.requestId());
        validateIdentity(persisted, command);
        return persisted;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long orderId, String safeReason) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        orderMapper.markFailedIfProcessing(orderId, normalizeReason(safeReason));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedAndRequestRelease(DrawOrderRow order, String safeReason, LocalDateTime occurredAt) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        String reason = normalizeReason(safeReason);
        if (orderMapper.markFailedIfProcessing(order.getId(), reason) != 1) {
            return;
        }
        outboxService.append(DrawEventEnvelope.create(
                DrawEventType.DRAW_RELEASE_REQUESTED, order.getRequestId(), order.getUserId(),
                order.getActivityId(), order.getId(), occurredAt,
                new DrawReleaseRequestedEvent(order.getDrawCount(), order.getDrawDate(), reason), objectMapper));
    }

    private void validateIdentity(DrawOrderRow order, NewDrawOrder command) {
        if (order == null
                || !Objects.equals(order.getUserId(), command.userId())
                || !Objects.equals(order.getActivityId(), command.activityId())
                || !Objects.equals(order.getDrawCount(), command.drawCount())) {
            throw new LotteryDomainException(LotteryErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) {
            normalized = DEFAULT_SAFE_REASON;
        }
        return normalized.length() <= MAX_REASON_LENGTH ? normalized : normalized.substring(0, MAX_REASON_LENGTH);
    }
}
