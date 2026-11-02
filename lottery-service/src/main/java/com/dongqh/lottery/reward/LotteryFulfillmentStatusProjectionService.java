package com.dongqh.lottery.reward;

import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import com.dongqh.lottery.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lottery.enums.drawtransaction.BenefitStatus;
import com.dongqh.lottery.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lottery.outbox.mapper.LotteryRewardQuarantineMapper;
import com.dongqh.lottery.outbox.mapper.MessageConsumeRecordMapper;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
public class LotteryFulfillmentStatusProjectionService {
    private static final String LOTTERY_SOURCE = "LOTTERY_BENEFIT";
    private static final Set<BenefitStatus> SHIPPING_STATUSES = EnumSet.of(
            BenefitStatus.CLAIM_PENDING, BenefitStatus.FULFILLING, BenefitStatus.SHIPPED,
            BenefitStatus.FULFILLMENT_FAILED);

    private final UserBenefitMapper benefits;
    private final LotteryRewardQuarantineMapper quarantines;
    private final MessageConsumeRecordMapper consumeRecords;
    private final String consumerName;

    public LotteryFulfillmentStatusProjectionService(
            UserBenefitMapper benefits,
            LotteryRewardQuarantineMapper quarantines,
            MessageConsumeRecordMapper consumeRecords,
            @Value("${luckyhub.lottery.fulfillment-status-consumer-name:lottery-fulfillment-status-projection}")
            String consumerName
    ) {
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName must not be blank");
        }
        this.benefits = benefits;
        this.quarantines = quarantines;
        this.consumeRecords = consumeRecords;
        this.consumerName = consumerName;
    }

    @Transactional
    public void consume(FulfillmentStatusChangedEventV1 event) {
        validate(event);
        if (alreadyConsumed(event.eventId())) {
            return;
        }
        if (!LOTTERY_SOURCE.equals(event.sourceType())) {
            consumeRecords.insertIfAbsent(event.eventId(), consumerName);
            return;
        }

        Long benefitId = positiveId(event.sourceId());
        UserBenefitRow benefit = benefitId == null ? null : benefits.selectByIdForUpdate(benefitId);
        if (benefit == null) {
            quarantine(event, benefit, benefitId, RewardQuarantineReason.FULFILLMENT_TASK_IDENTITY_MISMATCH);
            consumeRecords.insertIfAbsent(event.eventId(), consumerName);
            return;
        }
        if (benefit.getRewardType() != RewardType.PRODUCT || benefit.getPrizeType() != PrizeType.PHYSICAL) {
            consumeRecords.insertIfAbsent(event.eventId(), consumerName);
            return;
        }
        if (!matches(event, benefit, benefitId)) {
            quarantine(event, benefit, benefitId, RewardQuarantineReason.FULFILLMENT_TASK_IDENTITY_MISMATCH);
            consumeRecords.insertIfAbsent(event.eventId(), consumerName);
            return;
        }
        boolean projected = projectPhysical(event, benefit);
        if (!projected) {
            quarantine(event, benefit, benefitId, RewardQuarantineReason.FULFILLMENT_STATUS_UNSUPPORTED);
        }
        consumeRecords.insertIfAbsent(event.eventId(), consumerName);
    }

    private boolean projectPhysical(FulfillmentStatusChangedEventV1 event, UserBenefitRow benefit) {
        if (benefit.getShippingOrderId() == null || event.shippingNo() == null || event.shippingNo().isBlank()) {
            return false;
        }
        BenefitStatus target = switch (event.status()) {
            case "FULFILLING" -> BenefitStatus.FULFILLING;
            case "SHIPPED", "IN_TRANSIT" -> BenefitStatus.SHIPPED;
            case "DELIVERED" -> BenefitStatus.DELIVERED;
            case "FAILED" -> BenefitStatus.FULFILLMENT_FAILED;
            case "TERMINATED" -> BenefitStatus.FULFILLMENT_TERMINATED;
            default -> null;
        };
        if (target == null || benefit.getStatus() == target) {
            return target != null;
        }
        if (!SHIPPING_STATUSES.contains(benefit.getStatus())
                && benefit.getStatus() != BenefitStatus.FULFILLMENT_TERMINATED
                && benefit.getStatus() != BenefitStatus.DELIVERED) {
            return false;
        }
        if (benefit.getStatus() == BenefitStatus.DELIVERED
                || benefit.getStatus() == BenefitStatus.FULFILLMENT_TERMINATED) {
            return target == BenefitStatus.DELIVERED && benefit.getStatus() == BenefitStatus.DELIVERED;
        }
        return benefits.transitionStatus(benefit.getId(), benefit.getStatus(), target) == 1;
    }

    private boolean matches(FulfillmentStatusChangedEventV1 event, UserBenefitRow benefit, Long benefitId) {
        if (!Long.toString(benefitId).equals(event.sourceId())
                || !Long.valueOf(benefitId).equals(benefit.getId())) {
            return false;
        }
        if (benefit.getRewardType() == RewardType.PRODUCT && benefit.getPrizeType() == PrizeType.PHYSICAL) {
            return benefit.getShippingOrderId() != null
                    && ("LOGISTICS-" + benefit.getShippingOrderId()).equals(event.fulfillmentNo());
        }
        return false;
    }

    private void quarantine(FulfillmentStatusChangedEventV1 event, UserBenefitRow benefit, Long benefitId,
                            RewardQuarantineReason reason) {
        quarantines.insertIfAbsent(event.eventId(), event.fulfillmentNo(), null,
                benefit == null ? null : benefit.getDrawRecordId(), benefitId,
                benefit == null ? null : benefit.getPrizeId(),
                benefit == null ? null : benefit.getRewardDefinitionId(), reason.name());
    }

    private boolean alreadyConsumed(String eventId) {
        return consumeRecords.countByEventAndConsumer(eventId, consumerName) > 0;
    }

    private Long positiveId(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException | NullPointerException ignored) {
            return null;
        }
    }

    private void validate(FulfillmentStatusChangedEventV1 event) {
        if (event == null || blank(event.eventId()) || event.eventId().length() > 64
                || blank(event.fulfillmentNo()) || event.fulfillmentNo().length() > 64
                || blank(event.sourceType()) || blank(event.sourceId()) || blank(event.status())
                || event.occurredAt() == null) {
            throw new IllegalArgumentException("Fulfillment 状态事件无效");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
