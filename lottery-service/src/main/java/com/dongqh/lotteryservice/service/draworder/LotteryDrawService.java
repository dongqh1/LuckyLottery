package com.dongqh.lotteryservice.service.draworder;

import com.dongqh.lotteryservice.config.LotteryProperties;
import com.dongqh.lotteryservice.exception.LotteryDomainException;
import com.dongqh.lotteryservice.enums.drawchance.DrawChanceReservationStatus;
import com.dongqh.lotteryservice.domain.model.drawchance.DrawChanceReservationCommand;
import com.dongqh.lotteryservice.domain.model.drawchance.DrawChanceReservationResult;
import com.dongqh.lotteryservice.service.drawchance.DrawChanceReservationService;
import com.dongqh.lotteryservice.service.draweligibility.DrawEligibilityService;
import com.dongqh.lotteryservice.domain.model.draweligibility.DrawEligibility;
import com.dongqh.lotteryservice.domain.dto.draworder.DrawCommand;
import com.dongqh.lotteryservice.domain.po.draworder.DrawOrderRow;
import com.dongqh.lotteryservice.enums.draworder.DrawOrderStatus;
import com.dongqh.lotteryservice.mapper.draworder.DrawOrderMapper;
import com.dongqh.lotteryservice.domain.model.draworder.NewDrawOrder;
import com.dongqh.lotteryservice.domain.vo.draworder.DrawOrderView;
import com.dongqh.lotteryservice.domain.vo.draworder.DrawResultView;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.enums.drawtransaction.DrawResultType;
import com.dongqh.lotteryservice.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lotteryservice.domain.model.drawtransaction.DrawExecutionContext;
import com.dongqh.lotteryservice.domain.model.drawtransaction.DrawExecutionResult;
import com.dongqh.lotteryservice.domain.model.drawtransaction.DrawResultItem;
import com.dongqh.lotteryservice.service.drawtransaction.DrawTransactionService;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import com.dongqh.lotteryservice.lock.DrawLockService;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
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
    private final DrawOrderLifecycleService lifecycle;
    private final DrawTransactionService transactions;
    private final DrawChanceReservationService drawChances;

    public LotteryDrawService(DrawOrderMapper orders, DrawRecordMapper records,
                              DrawEligibilityService eligibility, DrawLockService locks,
                              DrawOrderLifecycleService lifecycle,
                              DrawTransactionService transactions, DrawChanceReservationService drawChances) {
        this.orders = orders;
        this.records = records;
        this.eligibility = eligibility;
        this.locks = locks;
        this.lifecycle = lifecycle;
        this.transactions = transactions;
        this.drawChances = drawChances;
    }

    /**
     * 执行一次抽奖请求：校验请求、预留次数、创建订单、完成抽奖并返回结果。
     * 每个 requestId 只能对应一个抽奖订单，失败时由生命周期服务负责补偿。
     */
    public DrawOrderView draw(DrawCommand command) {
        validate(command);
        Long contextUserId = UserContext.getUser();
        if (contextUserId == null || contextUserId <= 0) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_ACCESS_DENIED);
        }
        long userId = contextUserId;
        DrawOrderRow existing = orders.selectByRequestId(command.requestId());
        if (existing != null) return resolveExisting(existing, command, userId);

        DrawEligibility drawEligibility = eligibility.load(command.activityId());
        LocalDate drawDate = drawEligibility.evaluatedAt().toLocalDate();
        DrawOrderRow[] createdOrder = new DrawOrderRow[1];
        try {
            LockedDraw locked = locks.execute(command.activityId(), userId, () -> {
                DrawOrderRow second = orders.selectByRequestId(command.requestId());
                if (second != null) return new LockedDraw(resolveExisting(second, command, userId), null, null);

                DrawChanceReservationCommand chanceCommand = new DrawChanceReservationCommand(
                        command.requestId(), command.activityId(), userId, command.drawCount(), drawDate);

                DrawChanceReservationResult bonus = drawChances.reserve(chanceCommand);
                requireActiveChance(bonus);

                DrawOrderRow processing;
                try {
                    processing = lifecycle.createProcessing(new NewDrawOrder(command.requestId(), userId,
                            command.activityId(), command.drawCount(), drawDate));
                } catch (RuntimeException error) {
                    drawChances.release(command.requestId());
                    throw error;
                }
                createdOrder[0] = processing;
                return new LockedDraw(null, processing, drawDate);
            });

            if (locked.existingView() != null) return locked.existingView();
            DrawOrderRow order = locked.order();
            DrawExecutionResult result = transactions.execute(new DrawExecutionContext(order.getId(),
                    command.requestId(), userId, command.activityId(), command.drawCount(),
                    locked.drawDate(), drawEligibility.noWinWeight(), drawEligibility.prizes(),
                    drawEligibility.evaluatedAt()));
            return fromExecution(order, result);
        } catch (LotteryDomainException error) {
            compensateIfNeeded(createdOrder[0], drawEligibility.evaluatedAt());
            throw error;
        } catch (RuntimeException error) {
            compensateIfNeeded(createdOrder[0], drawEligibility.evaluatedAt());
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
        return new DrawResultView(item.recordId(), item.sequenceNo(), item.resultType(), item.prizeId(),
                item.prizeName(), item.prizeType(), item.prizeImageUrl(), item.benefitId(), item.itemId());
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
                              LocalDate drawDate) {
    }
}
