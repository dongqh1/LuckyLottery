package com.dongqh.lottery.service.benefit;

import com.dongqh.hmapi.client.LogisticsClient;
import com.dongqh.hmapi.dto.CreateShippingOrderRequest;
import com.dongqh.hmapi.dto.ShippingOrderDTO;
import com.dongqh.lottery.reward.CurrentLotteryReward;
import com.dongqh.lottery.reward.LotteryCurrentRewardResolver;
import com.dongqh.lottery.reward.RewardIdentityMismatchException;
import com.dongqh.lottery.domain.dto.benefit.ClaimPhysicalBenefitCommand;
import com.dongqh.lottery.exception.LotteryDomainException;
import com.dongqh.lottery.domain.po.draworder.DrawOrderRow;
import com.dongqh.lottery.enums.draworder.DrawOrderStatus;
import com.dongqh.lottery.mapper.draworder.DrawOrderMapper;
import com.dongqh.lottery.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lottery.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lottery.enums.drawtransaction.BenefitStatus;
import com.dongqh.lottery.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lottery.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lottery.enums.LotteryErrorCode;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class LotteryPhysicalBenefitClaimService {
    private static final String SOURCE_TYPE = "LOTTERY_BENEFIT";

    private final UserBenefitMapper benefits;
    private final DrawRecordMapper records;
    private final DrawOrderMapper orders;
    private final LogisticsClient logistics;
    private final LotteryCurrentRewardResolver currentRewards;

    public LotteryPhysicalBenefitClaimService(UserBenefitMapper benefits, DrawRecordMapper records,
                                              DrawOrderMapper orders, LogisticsClient logistics,
                                              LotteryCurrentRewardResolver currentRewards) {
        this.benefits = benefits;
        this.records = records;
        this.orders = orders;
        this.logistics = logistics;
        this.currentRewards = currentRewards;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderDTO claim(long userId, long benefitId, String authorization,
                                             ClaimPhysicalBenefitCommand command) {
        if (userId <= 0 || benefitId <= 0 || authorization == null || authorization.isBlank()) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_NOT_ALLOWED);
        }
        String requestId = normalizeRequestId(command);
        UserBenefitRow benefit = benefits.selectByIdForUpdate(benefitId);
        if (benefit == null) {
            throw error(LotteryErrorCode.BENEFIT_NOT_FOUND);
        }
        if (!Objects.equals(benefit.getUserId(), userId)) {
            throw error(LotteryErrorCode.BENEFIT_ACCESS_DENIED);
        }
        verifyDrawIdentity(benefit, userId);
        if (benefit.getShippingOrderId() != null) {
            if (!claimLifecycle(benefit.getStatus())) {
                throw error(LotteryErrorCode.BENEFIT_CLAIM_NOT_ALLOWED);
            }
            return existingShipping(benefit.getShippingOrderId());
        }

        CurrentLotteryReward current;
        try {
            current = currentRewards.resolve(benefit.getPrizeId());
        } catch (RewardIdentityMismatchException mismatch) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_NOT_ALLOWED);
        }
        if (current.prize().getPrizeType() != PrizeType.PHYSICAL
                || current.definition().getRewardType() != RewardType.PRODUCT
                || current.item() == null
                || !Objects.equals(current.item().getId(), current.definition().getTargetId())) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_NOT_ALLOWED);
        }
        int quantity;
        try {
            quantity = Math.toIntExact(current.definition().getQuantity());
        } catch (ArithmeticException exception) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_IDENTITY_CONFLICT);
        }
        if (current.item().getStock() == null || current.item().getStock() < quantity) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_NOT_ALLOWED);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        if (benefit.getStatus() != BenefitStatus.CLAIM_PENDING) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_NOT_ALLOWED);
        }
        if (benefit.getClaimDeadline() == null || !now.isBefore(benefit.getClaimDeadline())) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_EXPIRED);
        }

        ShippingOrderDTO shipping = logistics.createShippingOrder(authorization,
                new CreateShippingOrderRequest(SOURCE_TYPE, Long.toString(benefitId), userId,
                        command.addressId(), current.item().getId(), itemCode(current.item().getId()),
                        current.item().getName(), current.item().getImage(), quantity, requestId));
        if (shipping == null || shipping.id() == null || shipping.id() <= 0
                || !("LOGISTICS-" + shipping.id()).equals(shipping.fulfillmentNo())
                || shipping.shippingNo() == null || shipping.shippingNo().isBlank()
                || shipping.addressSnapshotId() == null || shipping.addressSnapshotId() <= 0) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_IDENTITY_CONFLICT);
        }
        if (benefits.markClaimed(benefitId, shipping.id(), now) != 1) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_IDENTITY_CONFLICT);
        }
        return shipping;
    }

    private ShippingOrderDTO existingShipping(long shippingOrderId) {
        ShippingOrderDTO shipping = logistics.getShippingOrdersByIds(List.of(shippingOrderId))
                .stream()
                .filter(order -> Objects.equals(order.id(), shippingOrderId))
                .findFirst()
                .orElse(null);
        if (shipping == null || !("LOGISTICS-" + shippingOrderId).equals(shipping.fulfillmentNo())
                || shipping.shippingNo() == null || shipping.shippingNo().isBlank()
                || shipping.addressSnapshotId() == null || shipping.addressSnapshotId() <= 0) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_IDENTITY_CONFLICT);
        }
        return shipping;
    }

    private void verifyDrawIdentity(UserBenefitRow benefit, long userId) {
        DrawRecordRow record = benefit.getDrawRecordId() == null ? null : records.selectById(benefit.getDrawRecordId());
        DrawOrderRow order = record == null || record.getOrderId() == null
                ? null : orders.selectById(record.getOrderId());
        if (record == null || order == null || order.getStatus() != DrawOrderStatus.SUCCESS
                || !Objects.equals(order.getUserId(), userId)
                || !Objects.equals(record.getUserId(), userId)
                || !Objects.equals(record.getId(), benefit.getDrawRecordId())
                || !Objects.equals(record.getPrizeId(), benefit.getPrizeId())) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_IDENTITY_CONFLICT);
        }
    }

    private boolean claimLifecycle(BenefitStatus status) {
        return status == BenefitStatus.CLAIMED || status == BenefitStatus.FULFILLING
                || status == BenefitStatus.SHIPPED || status == BenefitStatus.DELIVERED
                || status == BenefitStatus.FULFILLMENT_FAILED || status == BenefitStatus.FULFILLMENT_TERMINATED;
    }

    private String normalizeRequestId(ClaimPhysicalBenefitCommand command) {
        if (command == null || command.addressId() == null || command.addressId() <= 0
                || command.requestId() == null || command.requestId().isBlank()) {
            throw error(LotteryErrorCode.BENEFIT_QUERY_INVALID);
        }
        String raw = command.requestId().trim();
        try {
            String normalized = UUID.fromString(raw).toString();
            if (!normalized.equalsIgnoreCase(raw)) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw error(LotteryErrorCode.BENEFIT_QUERY_INVALID);
        }
    }

    private String itemCode(long itemId) {
        return "HMALL-ITEM-" + itemId;
    }

    private LotteryDomainException error(LotteryErrorCode code) {
        return new LotteryDomainException(code);
    }
}
