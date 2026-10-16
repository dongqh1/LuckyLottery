package com.dongqh.luckyhub.service.lottery;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dongqh.luckyhub.domain.po.activity.MarketingActivity;
import com.dongqh.luckyhub.enums.activity.ActivityStatus;
import com.dongqh.luckyhub.mapper.activity.MarketingActivityMapper;
import com.dongqh.luckyhub.mapper.activity.MarketingActivityPrizeMapper;
import com.dongqh.luckyhub.mapper.benefit.UserBenefitMapper;
import com.dongqh.luckyhub.mapper.lottery.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.mapper.lottery.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.service.lottery.impl.LotteryQueryServiceImpl;
import com.dongqh.luckyhub.domain.vo.lottery.LotteryPrizeView;
import com.dongqh.luckyhub.enums.prize.PrizeType;
import com.dongqh.luckyhub.mapper.prize.MarketingPrizeMapper;
import com.dongqh.luckyhub.service.rbac.DataScopeService;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LotteryQueryServiceTests {
    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MarketingActivity.class);
    }

    private final MarketingActivityMapper activities = mock(MarketingActivityMapper.class);
    private final MarketingActivityPrizeMapper activityPrizes = mock(MarketingActivityPrizeMapper.class);
    private final LotteryQueryServiceImpl service = new LotteryQueryServiceImpl(
            activities, activityPrizes, mock(MarketingPrizeMapper.class),
            mock(LotteryDrawOrderMapper.class), mock(LotteryDrawRecordMapper.class),
            mock(UserBenefitMapper.class), mock(DataScopeService.class));

    @Test
    void currentActivityQueryRequiresRunningTimeWindowStableOrderAndLimit() {
        MarketingActivity activity = new MarketingActivity();
        activity.setId(8L); activity.setActivityName("当前活动"); activity.setStatus(ActivityStatus.RUNNING);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        when(activities.selectList(any())).thenReturn(List.of(activity));

        assertThat(service.listActivities()).extracting(view -> view.id()).containsExactly(8L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<MarketingActivity>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(activities).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertThat(query.getValue().getParamNameValuePairs()).containsValue(ActivityStatus.RUNNING);
        assertThat(sql).contains("start_time <=", "end_time >=", "ORDER BY end_time ASC,id ASC", "LIMIT 100");
    }

    @Test
    void visiblePrizeQueryDelegatesToBoundedJoinProjectionWithoutClientSideTruncation() {
        MarketingActivity activity = new MarketingActivity(); activity.setId(8L);
        when(activities.selectById(8L)).thenReturn(activity);
        List<LotteryPrizeView> visible = java.util.stream.IntStream.rangeClosed(1, 100)
                .mapToObj(i -> new LotteryPrizeView((long) i, "奖品" + i, PrizeType.PHYSICAL, null, i))
                .toList();
        when(activityPrizes.selectVisiblePrizeViews(8L)).thenReturn(visible);

        assertThat(service.listPrizes(8L)).containsExactlyElementsOf(visible).hasSize(100);
        verify(activityPrizes).selectVisiblePrizeViews(8L);
    }

    @Test
    void visiblePrizeMapperFiltersEnabledRowsBeforeStableLimitAndProjectsNoStockOrWeight() throws Exception {
        Select select = MarketingActivityPrizeMapper.class
                .getMethod("selectVisiblePrizeViews", long.class).getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").toLowerCase();

        assertThat(sql).contains("join marketing_prize", "left join reward_definition",
                "rd.reward_type = 'product'", "rd.target_id", "hmall_item_id",
                "ap.remaining_stock > 0", "p.status = 1",
                "order by ap.sort_order asc, ap.id asc", "limit 100");
        assertThat(sql.indexOf("p.status = 1")).isLessThan(sql.indexOf("limit 100"));
        assertThat(sql).doesNotContain("ap.weight", "ap.total_stock");
    }
}
