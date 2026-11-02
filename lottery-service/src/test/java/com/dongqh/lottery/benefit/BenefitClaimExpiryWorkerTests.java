package com.dongqh.lottery.benefit;

import com.dongqh.lottery.domain.po.activityprize.ActivityPrizeRow;
import com.dongqh.lottery.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lottery.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lottery.enums.drawtransaction.BenefitStatus;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;
import com.dongqh.lottery.mapper.activityprize.ActivityPrizeMapper;
import com.dongqh.lottery.mapper.drawtransaction.ActivityPrizeInventoryMapper;
import com.dongqh.lottery.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lottery.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lottery.service.benefit.BenefitClaimExpiryWorker;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BenefitClaimExpiryWorkerTests {
    @Test
    void expiresCurrentPhysicalBenefitAndReturnsActivityPrizeStock() {
        UserBenefitMapper benefits = mock(UserBenefitMapper.class);
        DrawRecordMapper records = mock(DrawRecordMapper.class);
        ActivityPrizeMapper activityPrizes = mock(ActivityPrizeMapper.class);
        ActivityPrizeInventoryMapper inventory = mock(ActivityPrizeInventoryMapper.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 0);

        UserBenefitRow benefit = new UserBenefitRow();
        benefit.setId(41L);
        benefit.setDrawRecordId(31L);
        benefit.setUserId(7L);
        benefit.setPrizeId(51L);
        benefit.setPrizeType(PrizeType.PHYSICAL);
        benefit.setRewardType(RewardType.PRODUCT);
        benefit.setStatus(BenefitStatus.CLAIM_PENDING);
        benefit.setClaimDeadline(now.minusSeconds(1));
        when(benefits.selectByIdForUpdate(41L)).thenReturn(benefit);

        DrawRecordRow draw = new DrawRecordRow();
        draw.setId(31L);
        draw.setUserId(7L);
        draw.setActivityId(9L);
        draw.setPrizeId(51L);
        when(records.selectById(31L)).thenReturn(draw);
        ActivityPrizeRow relation = new ActivityPrizeRow();
        relation.setId(81L);
        when(activityPrizes.lockByActivityAndPrize(9L, 51L)).thenReturn(relation);
        when(inventory.incrementIfBelowTotal(81L)).thenReturn(1);
        when(benefits.markClaimExpired(41L, now)).thenReturn(1);

        BenefitClaimExpiryWorker worker = new BenefitClaimExpiryWorker(benefits, records, activityPrizes, inventory);

        assertThat(worker.expireOne(41L, now)).isTrue();
        verify(inventory).incrementIfBelowTotal(81L);
        verify(benefits).markClaimExpired(41L, now);
    }
}
