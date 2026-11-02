package com.dongqh.lottery.reward;

import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import com.dongqh.lottery.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lottery.enums.drawtransaction.BenefitStatus;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;
import com.dongqh.lottery.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lottery.outbox.mapper.LotteryRewardQuarantineMapper;
import com.dongqh.lottery.outbox.mapper.MessageConsumeRecordMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LotteryFulfillmentStatusProjectionServiceTests {
    @Test
    void ignoresLegacyVirtualRewardTaskStatusAfterDirectGrantMigration() {
        UserBenefitMapper benefits = mock(UserBenefitMapper.class);
        LotteryRewardQuarantineMapper quarantines = mock(LotteryRewardQuarantineMapper.class);
        MessageConsumeRecordMapper consumes = mock(MessageConsumeRecordMapper.class);
        UserBenefitRow benefit = new UserBenefitRow();
        benefit.setId(41L);
        benefit.setDrawRecordId(31L);
        benefit.setPrizeId(51L);
        benefit.setPrizeType(PrizeType.POINTS);
        benefit.setRewardType(RewardType.POINTS);
        benefit.setRewardDefinitionId(61L);
        benefit.setStatus(BenefitStatus.PENDING);
        when(benefits.selectByIdForUpdate(41L)).thenReturn(benefit);
        when(consumes.countByEventAndConsumer("event-1", "test-projection")).thenReturn(0);

        LotteryFulfillmentStatusProjectionService service = new LotteryFulfillmentStatusProjectionService(
                benefits, quarantines, consumes, "test-projection");
        service.consume(new FulfillmentStatusChangedEventV1("event-1", "LOTTERY-BENEFIT-41", null,
                "LOTTERY_BENEFIT", "41", "SUCCEEDED", LocalDateTime.of(2026, 10, 21, 12, 0)));

        verify(benefits, never()).transitionStatus(any(), any(), any());
        verify(consumes).insertIfAbsent("event-1", "test-projection");
        verify(quarantines, never()).insertIfAbsent(anyString(), anyString(), any(), any(), any(),
                any(), any(), anyString());
    }
}
