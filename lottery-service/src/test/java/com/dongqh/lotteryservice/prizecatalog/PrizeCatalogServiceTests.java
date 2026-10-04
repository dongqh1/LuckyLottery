package com.dongqh.lotteryservice.prizecatalog;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeCatalogException;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeErrorCode;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeLevel;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeType;
import com.dongqh.lotteryservice.prizecatalog.domain.RewardType;
import com.dongqh.lotteryservice.prizecatalog.dto.CreatePrizeCommand;
import com.dongqh.lotteryservice.prizecatalog.dto.PrizeQuery;
import com.dongqh.lotteryservice.prizecatalog.dto.UpdatePrizeCommand;
import com.dongqh.lotteryservice.prizecatalog.entity.PrizeRow;
import com.dongqh.lotteryservice.prizecatalog.entity.RewardDefinitionRow;
import com.dongqh.lotteryservice.prizecatalog.mapper.PrizeCatalogMapper;
import com.dongqh.lotteryservice.prizecatalog.mapper.RewardDefinitionMapper;
import com.dongqh.lotteryservice.prizecatalog.service.PrizeCatalogService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrizeCatalogServiceTests {
    private PrizeCatalogMapper mapper;
    private RewardDefinitionMapper rewards;
    private PrizeCatalogService service;

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PrizeRow.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(PrizeCatalogMapper.class);
        rewards = mock(RewardDefinitionMapper.class);
        service = new PrizeCatalogService(mapper, rewards);
    }

    @Test
    void createNormalizesFieldsBindsCompatibleRewardAndStartsEnabled() {
        when(rewards.selectById(7L)).thenReturn(reward(7L, RewardType.COUPON, 1));
        when(mapper.insert(any(PrizeRow.class))).thenAnswer(call -> {
            ((PrizeRow) call.getArgument(0)).setId(9L);
            return 1;
        });

        var view = service.create(new CreatePrizeCommand("  咖啡券  ", PrizeType.COUPON,
                PrizeLevel.FIRST, " https://cdn.example/prize.jpg ", " 说明 ", true, 7L));

        ArgumentCaptor<PrizeRow> inserted = ArgumentCaptor.forClass(PrizeRow.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getPrizeName()).isEqualTo("咖啡券");
        assertThat(inserted.getValue().getStatus()).isEqualTo(1);
        assertThat(inserted.getValue().getRewardDefinitionId()).isEqualTo(7L);
        assertThat(view.id()).isEqualTo(9L);
        assertThat(view.imageUrl()).isEqualTo("https://cdn.example/prize.jpg");
        assertThat(view.description()).isEqualTo("说明");
        assertThat(view.rewardType()).isEqualTo(RewardType.COUPON);
    }

    @Test
    void createRejectsMissingDisabledAndIncompatibleRewards() {
        var command = create(7L, PrizeType.COUPON);
        error(() -> service.create(create(null, PrizeType.COUPON)), PrizeErrorCode.REWARD_BINDING_INVALID);
        error(() -> service.create(command), PrizeErrorCode.REWARD_BINDING_INVALID);
        when(rewards.selectById(7L)).thenReturn(reward(7L, RewardType.COUPON, 0));
        error(() -> service.create(command), PrizeErrorCode.REWARD_BINDING_INVALID);
        when(rewards.selectById(7L)).thenReturn(reward(7L, RewardType.POINTS, 1));
        error(() -> service.create(command), PrizeErrorCode.REWARD_BINDING_INVALID);
        verify(mapper, never()).insert(any(PrizeRow.class));
    }

    @Test
    void getReturnsViewAndMissingPrizeUsesLegacyError() {
        error(() -> service.getById(404L), PrizeErrorCode.PRIZE_NOT_FOUND);
        PrizeRow row = prize(1L, 1, 7L);
        when(mapper.selectById(1L)).thenReturn(row);
        when(rewards.selectById(7L)).thenReturn(reward(7L, RewardType.POINTS, 1));
        assertThat(service.getById(1L).rewardType()).isEqualTo(RewardType.POINTS);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pageUsesFiltersOrderAndStableEnvelope() {
        Page<PrizeRow> result = new Page<>(2, 20, 21);
        result.setRecords(List.of(prize(5L, 1, null)));
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(result);
        PrizeQuery query = new PrizeQuery();
        query.setPage(2);
        query.setName("  咖啡  ");
        query.setType(PrizeType.COUPON);
        query.setStatus(1);

        var response = service.page(query);

        ArgumentCaptor<Page<PrizeRow>> page = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<Wrapper<PrizeRow>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(page.capture(), wrapper.capture());
        assertThat(page.getValue().getCurrent()).isEqualTo(2);
        assertThat(page.getValue().getSize()).isEqualTo(20);
        assertThat(wrapper.getValue().getCustomSqlSegment())
                .contains("prize_name", "prize_type", "status", "created_at", "id");
        assertThat(((LambdaQueryWrapper<PrizeRow>) wrapper.getValue()).getParamNameValuePairs().values())
                .contains("%咖啡%");
        assertThat(response.records()).hasSize(1);
        assertThat(response.total()).isEqualTo(21);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.pages()).isEqualTo(2);
    }

    @Test
    void updateKeepsStatusAndExistingBindingWhenOmitted() {
        PrizeRow row = prize(2L, 0, 7L);
        when(mapper.selectById(2L)).thenReturn(row);
        when(rewards.selectById(7L)).thenReturn(reward(7L, RewardType.POINTS, 1));

        var view = service.update(2L, new UpdatePrizeCommand(" 新奖品 ", PrizeType.POINTS,
                PrizeLevel.SECOND, "  ", " 新说明 ", false, null));

        verify(mapper).updateById(row);
        assertThat(view.prizeName()).isEqualTo("新奖品");
        assertThat(view.imageUrl()).isNull();
        assertThat(view.description()).isEqualTo("新说明");
        assertThat(view.status()).isZero();
        assertThat(view.rewardDefinitionId()).isEqualTo(7L);
    }

    @Test
    void updateRejectsBindingChangeAndIncompatibleType() {
        PrizeRow row = prize(2L, 1, 7L);
        when(mapper.selectById(2L)).thenReturn(row);
        error(() -> service.update(2L, new UpdatePrizeCommand("x", PrizeType.POINTS,
                PrizeLevel.SECOND, null, null, false, 8L)), PrizeErrorCode.REWARD_BINDING_INVALID);
        when(rewards.selectById(7L)).thenReturn(reward(7L, RewardType.COUPON, 1));
        error(() -> service.update(2L, new UpdatePrizeCommand("x", PrizeType.POINTS,
                PrizeLevel.SECOND, null, null, false, null)), PrizeErrorCode.REWARD_BINDING_INVALID);
        verify(mapper, never()).updateById(any(PrizeRow.class));
    }

    @Test
    void disableIsIdempotent() {
        PrizeRow enabled = prize(3L, 1, null);
        PrizeRow disabled = prize(4L, 0, null);
        when(mapper.selectById(3L)).thenReturn(enabled);
        when(mapper.selectById(4L)).thenReturn(disabled);
        service.disable(3L);
        service.disable(4L);
        assertThat(enabled.getStatus()).isZero();
        verify(mapper).updateById(enabled);
        verify(mapper, never()).updateById(disabled);
    }

    private CreatePrizeCommand create(Long rewardId, PrizeType type) {
        return new CreatePrizeCommand("奖品", type, PrizeLevel.FIRST, null, null, true, rewardId);
    }

    private PrizeRow prize(long id, int status, Long rewardId) {
        PrizeRow row = new PrizeRow();
        row.setId(id);
        row.setPrizeName("原奖品");
        row.setPrizeType(PrizeType.POINTS);
        row.setPrizeLevel(PrizeLevel.THIRD);
        row.setStackable(true);
        row.setStatus(status);
        row.setRewardDefinitionId(rewardId);
        return row;
    }

    private RewardDefinitionRow reward(long id, RewardType type, int status) {
        RewardDefinitionRow row = new RewardDefinitionRow();
        row.setId(id);
        row.setRewardType(type);
        row.setStatus(status);
        return row;
    }

    private void error(Runnable action, PrizeErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PrizeCatalogException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(expected);
            assertThat(error.getMessage()).isEqualTo(expected.message());
        });
    }
}
