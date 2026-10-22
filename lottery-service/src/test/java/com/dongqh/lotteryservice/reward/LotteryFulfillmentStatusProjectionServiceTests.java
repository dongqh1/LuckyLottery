package com.dongqh.lotteryservice.reward;

import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import com.dongqh.lotteryservice.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lotteryservice.outbox.mapper.LotteryRewardQuarantineMapper;
import com.dongqh.lotteryservice.outbox.mapper.MessageConsumeRecordMapper;
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
