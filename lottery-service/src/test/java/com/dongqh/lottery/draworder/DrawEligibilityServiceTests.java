package com.dongqh.lottery.draworder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.lottery.config.LotteryProperties;
import com.dongqh.lottery.domain.model.draweligibility.DrawEligibility;
import com.dongqh.lottery.domain.model.drawtransaction.DrawPrizeCandidate;
import com.dongqh.lottery.domain.po.activityprize.ActivityPrizeRow;
import com.dongqh.lottery.domain.po.activityprize.ActivityRow;
import com.dongqh.lottery.domain.po.prizecatalog.PrizeRow;
import com.dongqh.lottery.domain.po.prizecatalog.RewardDefinitionRow;
import com.dongqh.lottery.enums.activityprize.ActivityStatus;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;
import com.dongqh.lottery.mapper.activityprize.ActivityMapper;
import com.dongqh.lottery.mapper.activityprize.ActivityPrizeMapper;
import com.dongqh.lottery.mapper.prizecatalog.PrizeCatalogMapper;
import com.dongqh.lottery.mapper.prizecatalog.RewardDefinitionMapper;
import com.dongqh.lottery.service.draweligibility.DrawEligibilityService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DrawEligibilityServiceTests {
    @Test
    void createsRequestLocalWeightedCandidateFromCurrentRewardDefinition() {
        ActivityMapper activities = mock(ActivityMapper.class);
        ActivityPrizeMapper activityPrizes = mock(ActivityPrizeMapper.class);
        PrizeCatalogMapper prizes = mock(PrizeCatalogMapper.class);
        RewardDefinitionMapper definitions = mock(RewardDefinitionMapper.class);
        ItemClient items = mock(ItemClient.class);
        CouponClient coupons = mock(CouponClient.class);
        MembershipClient memberships = mock(MembershipClient.class);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(
                java.time.temporal.ChronoUnit.MILLIS);

        ActivityRow activity = new ActivityRow();
        activity.setId(9L);
        activity.setStatus(ActivityStatus.RUNNING);
        activity.setStartTime(now.minusDays(1));
        activity.setEndTime(now.plusDays(1));
        activity.setNoWinWeight(5);
        when(activities.selectById(9L)).thenReturn(activity);

        ActivityPrizeRow relation = new ActivityPrizeRow();
        relation.setId(81L);
        relation.setActivityId(9L);
        relation.setPrizeId(51L);
        relation.setWeight(10);
        relation.setRemainingStock(4);
        relation.setSortOrder(0);
        when(activityPrizes.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(relation));

        PrizeRow prize = new PrizeRow();
        prize.setId(51L);
        prize.setPrizeName("Points");
        prize.setPrizeType(PrizeType.POINTS);
        prize.setRewardDefinitionId(61L);
        prize.setStatus(1);
        when(prizes.selectByIds(List.of(51L))).thenReturn(List.of(prize));
        RewardDefinitionRow definition = new RewardDefinitionRow();
        definition.setId(61L);
        definition.setRewardType(RewardType.POINTS);
        definition.setQuantity(250L);
        definition.setStatus(1);
        when(definitions.selectByIds(List.of(61L))).thenReturn(List.of(definition));

        LotteryProperties properties = new LotteryProperties(ZoneId.of("Asia/Shanghai"),
                Duration.ofSeconds(1), Duration.ofMinutes(2), Duration.ofMinutes(1),
                Duration.ZERO, 10, Duration.ofSeconds(1), 100);
        DrawEligibilityService service = new DrawEligibilityService(activities, activityPrizes, prizes,
                definitions, items, coupons, memberships, properties);

        DrawEligibility eligibility = service.load(9L);

        DrawPrizeCandidate candidate = eligibility.prizes().get(0);
        assertThat(eligibility.activityId()).isEqualTo(9L);
        assertThat(eligibility.noWinWeight()).isEqualTo(5);
        assertThat(eligibility.evaluatedAt()).isNotNull();
        assertThat(candidate).isEqualTo(new DrawPrizeCandidate(81L, 51L, "Points", PrizeType.POINTS,
                null, 10L, 4, true, 61L, RewardType.POINTS, null, 250L));
    }
}
