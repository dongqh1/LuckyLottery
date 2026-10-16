package com.dongqh.lotteryservice.service.benefit;

import com.dongqh.lotteryservice.domain.po.activityprize.ActivityPrizeRow;
import com.dongqh.lotteryservice.mapper.activityprize.ActivityPrizeMapper;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.mapper.drawtransaction.ActivityPrizeInventoryMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lotteryservice.domain.model.drawtransaction.ProductRewardPayload;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class BenefitClaimExpiryWorker {

    private final UserBenefitMapper benefits;
    private final DrawRecordMapper records;
    private final ActivityPrizeMapper activityPrizes;
    private final ActivityPrizeInventoryMapper inventory;
    private final ObjectMapper json;

    public BenefitClaimExpiryWorker(UserBenefitMapper benefits, DrawRecordMapper records,
                                    ActivityPrizeMapper activityPrizes,
                                    ActivityPrizeInventoryMapper inventory, ObjectMapper json) {
        this.benefits = benefits;
        this.records = records;
        this.activityPrizes = activityPrizes;
        this.inventory = inventory;
        this.json = json;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean expireOne(long benefitId, LocalDateTime now) {
        UserBenefitRow benefit = benefits.selectByIdForUpdate(benefitId);
        if (benefit == null || benefit.getStatus() != BenefitStatus.CLAIM_PENDING
                || benefit.getClaimDeadline() == null || benefit.getClaimDeadline().isAfter(now)) {
            return false;
        }

        DrawRecordRow draw = benefit.getDrawRecordId() == null
                ? null : records.selectById(benefit.getDrawRecordId());
        ProductRewardPayload product = requireFrozenIdentity(benefit, draw);
        ActivityPrizeRow activityPrize = activityPrizes.lockByActivityAndPrize(
                draw.getActivityId(), draw.getPrizeId());
        if (activityPrize == null || activityPrize.getId() == null) {
            throw new IllegalStateException("活动奖品关系不存在");
        }
        if (inventory.incrementIfBelowTotal(activityPrize.getId()) != 1) {
            throw new IllegalStateException("奖品库存回补失败");
        }
        if (benefits.markClaimExpired(benefitId, now) != 1) {
            throw new IllegalStateException("权益过期状态转换失败");
        }
        return true;
    }

    private ProductRewardPayload requireFrozenIdentity(UserBenefitRow benefit, DrawRecordRow draw) {
        if (draw == null || benefit.getPrizeType() != PrizeType.PHYSICAL
                || benefit.getRewardType() != RewardType.PRODUCT
                || !Objects.equals(draw.getUserId(), benefit.getUserId())
                || !Objects.equals(draw.getPrizeId(), benefit.getPrizeId())
                || draw.getPrizeType() != benefit.getPrizeType()
                || !Objects.equals(draw.getRewardDefinitionId(), benefit.getRewardDefinitionId())
                || draw.getRewardType() != benefit.getRewardType()
                || !Objects.equals(draw.getRewardTargetId(), benefit.getRewardTargetId())
                || !Objects.equals(draw.getRewardQuantity(), benefit.getRewardQuantity())
                || !Objects.equals(draw.getRewardPayload(), benefit.getRewardPayload())
                || !Objects.equals(draw.getRewardFingerprint(), benefit.getRewardFingerprint())) {
            throw new IllegalStateException("冻结奖励身份不一致");
        }
        try {
            ProductRewardPayload product = json.readValue(benefit.getRewardPayload(), ProductRewardPayload.class);
            if (!Objects.equals(benefit.getRewardTargetId(), product.itemId())
                    || !Objects.equals(benefit.getRewardQuantity(), product.quantity())
                    || !Objects.equals(benefit.getQuantity(), product.quantity())) {
                throw new IllegalStateException("冻结商品奖励不一致");
            }
            return product;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("冻结商品奖励不可解析", exception);
        }
    }
}
