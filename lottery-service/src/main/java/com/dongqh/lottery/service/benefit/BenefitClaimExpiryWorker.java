package com.dongqh.lottery.service.benefit;

import com.dongqh.lottery.domain.po.activityprize.ActivityPrizeRow;
import com.dongqh.lottery.mapper.activityprize.ActivityPrizeMapper;
import com.dongqh.lottery.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lottery.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lottery.enums.drawtransaction.BenefitStatus;
import com.dongqh.lottery.mapper.drawtransaction.ActivityPrizeInventoryMapper;
import com.dongqh.lottery.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lottery.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class BenefitClaimExpiryWorker {

    private final UserBenefitMapper benefits;
    private final DrawRecordMapper records;
    private final ActivityPrizeMapper activityPrizes;
    private final ActivityPrizeInventoryMapper inventory;

    public BenefitClaimExpiryWorker(UserBenefitMapper benefits, DrawRecordMapper records,
                                    ActivityPrizeMapper activityPrizes,
                                    ActivityPrizeInventoryMapper inventory) {
        this.benefits = benefits;
        this.records = records;
        this.activityPrizes = activityPrizes;
        this.inventory = inventory;
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
        requireCurrentProduct(benefit, draw);
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

    private void requireCurrentProduct(UserBenefitRow benefit, DrawRecordRow draw) {
        if (draw == null || !Objects.equals(draw.getUserId(), benefit.getUserId())
                || !Objects.equals(draw.getPrizeId(), benefit.getPrizeId())
                || benefit.getPrizeType() != PrizeType.PHYSICAL
                || benefit.getRewardType() != RewardType.PRODUCT) {
            throw new IllegalStateException("抽奖记录与权益身份不一致");
        }
    }
}
