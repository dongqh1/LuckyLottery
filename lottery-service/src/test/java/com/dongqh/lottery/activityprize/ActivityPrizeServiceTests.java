package com.dongqh.lottery.activityprize;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dongqh.lottery.exception.activityprize.ActivityPrizeErrorCode;
import com.dongqh.lottery.exception.activityprize.ActivityPrizeException;
import com.dongqh.lottery.enums.activityprize.ActivityStatus;
import com.dongqh.lottery.enums.activityprize.PrizeLevel;
import com.dongqh.lottery.enums.activityprize.PrizeType;
import com.dongqh.lottery.domain.dto.activityprize.AddActivityPrizeCommand;
import com.dongqh.lottery.domain.dto.activityprize.UpdateActivityPrizeCommand;
import com.dongqh.lottery.domain.po.activityprize.ActivityPrizeRow;
import com.dongqh.lottery.domain.po.activityprize.ActivityRow;
import com.dongqh.lottery.domain.po.activityprize.PrizeRow;
import com.dongqh.lottery.mapper.activityprize.ActivityMapper;
import com.dongqh.lottery.mapper.activityprize.ActivityPrizeMapper;
import com.dongqh.lottery.mapper.activityprize.PrizeMapper;
import com.dongqh.lottery.service.activityprize.ActivityPrizeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityPrizeServiceTests {

    private ActivityMapper activities;
    private ActivityPrizeMapper relations;
    private PrizeMapper prizes;
    private ActivityPrizeService service;

    @BeforeEach
    void setUp() {
        activities = mock(ActivityMapper.class);
        relations = mock(ActivityPrizeMapper.class);
        prizes = mock(PrizeMapper.class);
        service = new ActivityPrizeService(activities, relations, prizes);
    }

    @Test
    void addInitializesRemainingStockAndReturnsPrizeDetails() {
        when(activities.selectById(5L)).thenReturn(activity(ActivityStatus.DRAFT));
        when(prizes.selectById(7L)).thenReturn(prize(1));

        var view = service.add(5L, new AddActivityPrizeCommand(7L, 20, 100, 1));

        ArgumentCaptor<ActivityPrizeRow> inserted = ArgumentCaptor.forClass(ActivityPrizeRow.class);
        verify(relations).insert(inserted.capture());
        assertThat(inserted.getValue().getActivityId()).isEqualTo(5L);
        assertThat(inserted.getValue().getPrizeId()).isEqualTo(7L);
        assertThat(inserted.getValue().getRemainingStock()).isEqualTo(100);
        assertThat(view.prizeName()).isEqualTo("咖啡券");
        assertThat(view.prizeType()).isEqualTo(PrizeType.COUPON);
        assertThat(view.prizeLevel()).isEqualTo(PrizeLevel.FIRST);
        assertThat(view.imageUrl()).isEqualTo("https://cdn.example/prize.png");
        assertThat(view.prizeStatus()).isEqualTo(1);
    }

    @Test
    void addRejectsMissingActivityPrizeAndDuplicateWithLegacyErrors() {
        error(() -> service.add(5L, add()), ActivityPrizeErrorCode.ACTIVITY_NOT_FOUND, 42001, "活动不存在");
        when(activities.selectById(5L)).thenReturn(activity(ActivityStatus.DRAFT));
        error(() -> service.add(5L, add()), ActivityPrizeErrorCode.ACTIVITY_PRIZE_NOT_FOUND, 42004, "活动奖品不存在");
        when(prizes.selectById(7L)).thenReturn(prize(1));
        when(relations.selectOne(any(Wrapper.class))).thenReturn(relation(100, 100));
        error(() -> service.add(5L, add()), ActivityPrizeErrorCode.ACTIVITY_PRIZE_DUPLICATE, 42005, "活动已关联该奖品");
        verify(relations, never()).insert(any(ActivityPrizeRow.class));
    }

    @Test
    void configurationRejectsDisabledOrEndedActivityAndDisabledPrize() {
        when(activities.selectById(5L)).thenReturn(activity(ActivityStatus.DISABLED));
        error(() -> service.add(5L, add()), ActivityPrizeErrorCode.ACTIVITY_STATE_CONFLICT, 42003,
                "当前活动状态不允许执行该操作");
        when(activities.selectById(5L)).thenReturn(activity(ActivityStatus.ENDED));
        error(() -> service.remove(5L, 7L), ActivityPrizeErrorCode.ACTIVITY_STATE_CONFLICT, 42003,
                "当前活动状态不允许执行该操作");
        when(activities.selectById(5L)).thenReturn(activity(ActivityStatus.SCHEDULED));
        when(prizes.selectById(7L)).thenReturn(prize(0));
        error(() -> service.add(5L, add()), ActivityPrizeErrorCode.ACTIVITY_HAS_DISABLED_PRIZE, 42007,
                "活动包含已禁用奖品");
    }

    @Test
    void listOrdersBySortOrderThenRelationIdAndIncludesPrizeDetails() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "test"), ActivityPrizeRow.class);
        when(activities.selectById(5L)).thenReturn(activity(ActivityStatus.ENDED));
        ActivityPrizeRow second = relation(50, 40);
        second.setId(12L);
        second.setSortOrder(2);
        ActivityPrizeRow first = relation(100, 80);
        first.setSortOrder(1);
        when(relations.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        when(prizes.selectBatchIds(List.of(7L, 7L))).thenReturn(List.of(prize(1)));

        var views = service.list(5L);

        assertThat(views).extracting(view -> view.id()).containsExactly(11L, 12L);
        assertThat(views).extracting(view -> view.prizeName()).containsExactly("咖啡券", "咖啡券");
        ArgumentCaptor<Wrapper<ActivityPrizeRow>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(relations).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("sort_order ASC", "id ASC");
    }

    @Test
    void listReturnsEmptyWithoutPrizeLookupButRequiresExistingActivity() {
        error(() -> service.list(5L), ActivityPrizeErrorCode.ACTIVITY_NOT_FOUND, 42001, "活动不存在");
        when(activities.selectById(5L)).thenReturn(activity(ActivityStatus.DRAFT));
        assertThat(service.list(5L)).isEmpty();
        verify(prizes, never()).selectBatchIds(any());
    }

    @Test
    void updatePreservesConsumedStockAndChangesConfigFields() {
        when(activities.selectById(5L)).thenReturn(activity(ActivityStatus.RUNNING));
        when(prizes.selectById(7L)).thenReturn(prize(1));
        ActivityPrizeRow relation = relation(100, 80);
        when(relations.selectOne(any(Wrapper.class))).thenReturn(relation);

        var view = service.update(5L, 7L, new UpdateActivityPrizeCommand(30, 150, 2));

        assertThat(relation.getRemainingStock()).isEqualTo(130);
        assertThat(relation.getTotalStock()).isEqualTo(150);
        assertThat(relation.getWeight()).isEqualTo(30);
        assertThat(relation.getSortOrder()).isEqualTo(2);
        assertThat(view.remainingStock()).isEqualTo(130);
        verify(relations).updateById(relation);
    }

    @Test
    void updateRejectsBelowConsumedAndMissingRelation() {
        when(activities.selectById(5L)).thenReturn(activity(ActivityStatus.DRAFT));
        when(prizes.selectById(7L)).thenReturn(prize(1));
        error(() -> service.update(5L, 7L, new UpdateActivityPrizeCommand(30, 150, 2)),
                ActivityPrizeErrorCode.ACTIVITY_PRIZE_NOT_FOUND, 42004, "活动奖品不存在");
        when(relations.selectOne(any(Wrapper.class))).thenReturn(relation(100, 20));
        error(() -> service.update(5L, 7L, new UpdateActivityPrizeCommand(30, 70, 2)),
                ActivityPrizeErrorCode.ACTIVITY_STOCK_BELOW_CONSUMED, 42009,
                "奖品总库存不能小于已消耗库存");
        verify(relations, never()).updateById(any(ActivityPrizeRow.class));
    }

    @Test
    void removeDeletesOnlyExistingRelation() {
        when(activities.selectById(5L)).thenReturn(activity(ActivityStatus.DRAFT));
        error(() -> service.remove(5L, 7L), ActivityPrizeErrorCode.ACTIVITY_PRIZE_NOT_FOUND, 42004,
                "活动奖品不存在");
        when(relations.selectOne(any(Wrapper.class))).thenReturn(relation(100, 80));

        service.remove(5L, 7L);

        verify(relations).deleteById(11L);
        verify(prizes, never()).deleteById(any(Long.class));
    }

    private AddActivityPrizeCommand add() {
        return new AddActivityPrizeCommand(7L, 20, 100, 1);
    }

    private ActivityRow activity(ActivityStatus status) {
        ActivityRow row = new ActivityRow();
        row.setId(5L);
        row.setStatus(status);
        return row;
    }

    private PrizeRow prize(int status) {
        PrizeRow row = new PrizeRow();
        row.setId(7L);
        row.setPrizeName("咖啡券");
        row.setPrizeType(PrizeType.COUPON);
        row.setPrizeLevel(PrizeLevel.FIRST);
        row.setImageUrl("https://cdn.example/prize.png");
        row.setStatus(status);
        return row;
    }

    private ActivityPrizeRow relation(int total, int remaining) {
        ActivityPrizeRow row = new ActivityPrizeRow();
        row.setId(11L);
        row.setActivityId(5L);
        row.setPrizeId(7L);
        row.setWeight(20);
        row.setTotalStock(total);
        row.setRemainingStock(remaining);
        row.setSortOrder(1);
        return row;
    }

    private void error(Runnable action, ActivityPrizeErrorCode code, int number, String message) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ActivityPrizeException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(code);
            assertThat(error.getErrorCode().code()).isEqualTo(number);
            assertThat(error.getMessage()).isEqualTo(message);
        });
    }
}
