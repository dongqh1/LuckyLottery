package com.dongqh.lotteryservice.service.benefit;

import com.dongqh.hmapi.client.FulfillmentClient;
import com.dongqh.hmapi.dto.CreateFulfillmentShippingOrderRequest;
import com.dongqh.hmapi.dto.FulfillmentShippingOrderDTO;
import com.dongqh.lotteryservice.domain.dto.benefit.ClaimPhysicalBenefitCommand;
import com.dongqh.lotteryservice.exception.LotteryDomainException;
import com.dongqh.lotteryservice.domain.po.draworder.DrawOrderRow;
import com.dongqh.lotteryservice.enums.draworder.DrawOrderStatus;
import com.dongqh.lotteryservice.mapper.draworder.DrawOrderMapper;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lotteryservice.domain.model.drawtransaction.ProductRewardPayload;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
public class LotteryPhysicalBenefitClaimService {
    private static final String SOURCE_TYPE = "LOTTERY_BENEFIT";

    private final UserBenefitMapper benefits;
    private final DrawRecordMapper records;
    private final DrawOrderMapper orders;
    private final FulfillmentClient fulfillment;
    private final ObjectMapper json;

    public LotteryPhysicalBenefitClaimService(UserBenefitMapper benefits, DrawRecordMapper records,
                                              DrawOrderMapper orders, FulfillmentClient fulfillment,
                                              ObjectMapper json) {
        this.benefits = benefits;
        this.records = records;
        this.orders = orders;
        this.fulfillment = fulfillment;
        this.json = json;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FulfillmentShippingOrderDTO claim(long userId, long benefitId, String authorization,
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
        ProductRewardPayload product = frozenProduct(benefit);
        verifyDrawIdentity(benefit, userId);

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        if (benefit.getShippingOrderId() == null) {
            if (benefit.getStatus() != BenefitStatus.CLAIM_PENDING) {
                throw error(LotteryErrorCode.BENEFIT_CLAIM_NOT_ALLOWED);
            }
            if (benefit.getClaimDeadline() == null || !now.isBefore(benefit.getClaimDeadline())) {
                throw error(LotteryErrorCode.BENEFIT_CLAIM_EXPIRED);
            }
        } else if (!claimLifecycle(benefit.getStatus())) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_NOT_ALLOWED);
        }

        FulfillmentShippingOrderDTO shipping = fulfillment.createShippingOrder(authorization,
                new CreateFulfillmentShippingOrderRequest(SOURCE_TYPE, Long.toString(benefitId), userId,
                        command.addressId(), product.itemId(), itemCode(product.itemId()), product.itemName(),
                        product.itemImage(), product.quantity(), requestId));
        if (shipping == null || shipping.id() == null || shipping.id() <= 0
                || !("LOGISTICS-" + shipping.id()).equals(shipping.fulfillmentNo())
                || shipping.shippingNo() == null || shipping.shippingNo().isBlank()
                || shipping.addressSnapshotId() == null || shipping.addressSnapshotId() <= 0) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_IDENTITY_CONFLICT);
        }
        if (benefit.getShippingOrderId() != null && !benefit.getShippingOrderId().equals(shipping.id())) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_IDENTITY_CONFLICT);
        }
        if (benefit.getShippingOrderId() == null
                && benefits.markClaimed(benefitId, shipping.id(), now) != 1) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_IDENTITY_CONFLICT);
        }
        return shipping;
    }

    private ProductRewardPayload frozenProduct(UserBenefitRow benefit) {
        if (benefit.getPrizeType() != PrizeType.PHYSICAL || benefit.getRewardType() != RewardType.PRODUCT
                || benefit.getRewardTargetId() == null || benefit.getRewardQuantity() == null
                || benefit.getRewardPayload() == null || benefit.getQuantity() == null) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_NOT_ALLOWED);
        }
        try {
            ProductRewardPayload product = json.readValue(benefit.getRewardPayload(), ProductRewardPayload.class);
            if (!Objects.equals(benefit.getRewardTargetId(), product.itemId())
                    || benefit.getRewardQuantity() != product.quantity()
                    || !Objects.equals(benefit.getQuantity(), product.quantity())) {
                throw error(LotteryErrorCode.BENEFIT_CLAIM_IDENTITY_CONFLICT);
            }
            return product;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw error(LotteryErrorCode.BENEFIT_CLAIM_IDENTITY_CONFLICT);
        }
    }

    private void verifyDrawIdentity(UserBenefitRow benefit, long userId) {
        DrawRecordRow record = benefit.getDrawRecordId() == null ? null : records.selectById(benefit.getDrawRecordId());
        DrawOrderRow order = record == null || record.getOrderId() == null
                ? null : orders.selectById(record.getOrderId());
        if (record == null || order == null || order.getStatus() != DrawOrderStatus.SUCCESS
                || !Objects.equals(order.getUserId(), userId)
                || !Objects.equals(record.getUserId(), userId)
                || !Objects.equals(record.getId(), benefit.getDrawRecordId())
                || !Objects.equals(record.getPrizeId(), benefit.getPrizeId())
                || record.getPrizeType() != benefit.getPrizeType()
                || !Objects.equals(record.getRewardDefinitionId(), benefit.getRewardDefinitionId())
                || record.getRewardType() != benefit.getRewardType()
                || !Objects.equals(record.getRewardTargetId(), benefit.getRewardTargetId())
                || !Objects.equals(record.getRewardQuantity(), benefit.getRewardQuantity())
                || !Objects.equals(record.getRewardPayload(), benefit.getRewardPayload())
                || !Objects.equals(record.getRewardFingerprint(), benefit.getRewardFingerprint())) {
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
