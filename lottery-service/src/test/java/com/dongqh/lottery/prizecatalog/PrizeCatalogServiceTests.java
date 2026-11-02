package com.dongqh.lottery.prizecatalog;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.lottery.exception.prizecatalog.PrizeCatalogException;
import com.dongqh.lottery.enums.prizecatalog.PrizeErrorCode;
import com.dongqh.lottery.enums.prizecatalog.PrizeLevel;
import com.dongqh.lottery.enums.prizecatalog.PrizeType;
import com.dongqh.lottery.enums.prizecatalog.RewardType;
import com.dongqh.lottery.domain.dto.prizecatalog.CreateRewardDefinitionCommand;
import com.dongqh.lottery.domain.dto.prizecatalog.CreatePrizeCommand;
import com.dongqh.lottery.domain.dto.prizecatalog.PrizeQuery;
import com.dongqh.lottery.domain.dto.prizecatalog.UpdatePrizeCommand;
import com.dongqh.lottery.domain.po.prizecatalog.PrizeRow;
import com.dongqh.lottery.domain.po.prizecatalog.RewardDefinitionRow;
import com.dongqh.lottery.mapper.prizecatalog.PrizeCatalogMapper;
import com.dongqh.lottery.mapper.prizecatalog.RewardDefinitionMapper;
import com.dongqh.lottery.domain.vo.prizecatalog.RewardDefinitionView;
import com.dongqh.lottery.service.prizecatalog.PrizeCatalogService;
import com.dongqh.lottery.service.prizecatalog.RewardDefinitionAdminService;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
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
    private RewardDefinitionAdminService rewardCreator;
    private PrizeCatalogService service;

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PrizeRow.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(PrizeCatalogMapper.class);
        rewards = mock(RewardDefinitionMapper.class);
        rewardCreator = mock(RewardDefinitionAdminService.class);
        service = new PrizeCatalogService(mapper, rewards, rewardCreator);
    }

    @Test
    void createBuildsRewardAndPrizeFromOneCommand() {
        when(rewardCreator.create(any(CreateRewardDefinitionCommand.class))).thenReturn(
                new RewardDefinitionView(7L, "PRIZE-test", "咖啡券", RewardType.COUPON,
                        31L, 2L, 1, null, null));
        when(rewards.selectById(7L)).thenReturn(reward(7L, RewardType.COUPON, 1, 31L, 2L));
        when(mapper.insert(any(PrizeRow.class))).thenAnswer(call -> {
            ((PrizeRow) call.getArgument(0)).setId(9L);
            return 1;
        });

        var view = service.create(new CreatePrizeCommand("  咖啡券  ", RewardType.COUPON,
                PrizeLevel.FIRST, " https://cdn.example/prize.jpg ", " 说明 ", true,
                31L, 2L));

        ArgumentCaptor<CreateRewardDefinitionCommand> reward = ArgumentCaptor.forClass(CreateRewardDefinitionCommand.class);
        verify(rewardCreator).create(reward.capture());
        assertThat(reward.getValue().rewardCode()).startsWith("PRIZE-");
        assertThat(reward.getValue().rewardName()).isEqualTo("咖啡券");
        assertThat(reward.getValue().rewardType()).isEqualTo(RewardType.COUPON);
        assertThat(reward.getValue().targetId()).isEqualTo(31L);
        assertThat(reward.getValue().quantity()).isEqualTo(2L);
        ArgumentCaptor<PrizeRow> inserted = ArgumentCaptor.forClass(PrizeRow.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getPrizeName()).isEqualTo("咖啡券");
        assertThat(inserted.getValue().getPrizeType()).isEqualTo(PrizeType.COUPON);
        assertThat(inserted.getValue().getStatus()).isEqualTo(1);
        assertThat(inserted.getValue().getRewardDefinitionId()).isEqualTo(7L);
        assertThat(view.id()).isEqualTo(9L);
        assertThat(view.imageUrl()).isEqualTo("https://cdn.example/prize.jpg");
        assertThat(view.description()).isEqualTo("说明");
        assertThat(view.rewardType()).isEqualTo(RewardType.COUPON);
        assertThat(view.targetId()).isEqualTo(31L);
        assertThat(view.quantity()).isEqualTo(2L);
    }

    @Test
    void createMapsProductRewardToPhysicalPrize() {
        when(rewardCreator.create(any(CreateRewardDefinitionCommand.class))).thenReturn(
                new RewardDefinitionView(12L, "PRIZE-product", "水杯", RewardType.PRODUCT,
                        88L, 1L, 1, null, null));
        when(rewards.selectById(12L)).thenReturn(reward(12L, RewardType.PRODUCT, 1, 88L, 1L));
        when(mapper.insert(any(PrizeRow.class))).thenAnswer(call -> {
            ((PrizeRow) call.getArgument(0)).setId(13L);
            return 1;
        });

        service.create(new CreatePrizeCommand("水杯", RewardType.PRODUCT, PrizeLevel.SECOND,
                null, null, false, 88L, 1L));

        ArgumentCaptor<PrizeRow> inserted = ArgumentCaptor.forClass(PrizeRow.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getPrizeType()).isEqualTo(PrizeType.PHYSICAL);
    }

    @Test
    void createBuildsMembershipRewardProductFromLevelAndDurationWithoutManualTargetId() {
        when(rewardCreator.createRewardProduct("LOTTERY-MEMBER-1", "新会员礼", "GOLD", 14))
                .thenReturn(new MembershipRewardProductDTO(22L, "LOTTERY-MEMBER-1", "GOLD", 14, 1));
        when(rewardCreator.create(any(CreateRewardDefinitionCommand.class))).thenReturn(
                new RewardDefinitionView(23L, "PRIZE-membership", "新会员礼", RewardType.MEMBERSHIP,
                        22L, 1L, 1, null, null));
        when(rewards.selectById(23L)).thenReturn(reward(23L, RewardType.MEMBERSHIP, 1, 22L, 1L));
        when(mapper.insert(any(PrizeRow.class))).thenAnswer(call -> {
            ((PrizeRow) call.getArgument(0)).setId(24L);
            return 1;
        });

        var result = service.create(new CreatePrizeCommand("新会员礼", RewardType.MEMBERSHIP,
                PrizeLevel.THIRD, null, null, false, null, 1L,
                "LOTTERY-MEMBER-1", "GOLD", 14));

        verify(rewardCreator).createRewardProduct("LOTTERY-MEMBER-1", "新会员礼", "GOLD", 14);
        ArgumentCaptor<CreateRewardDefinitionCommand> reward = ArgumentCaptor.forClass(CreateRewardDefinitionCommand.class);
        verify(rewardCreator).create(reward.capture());
        assertThat(reward.getValue().targetId()).isEqualTo(22L);
        assertThat(result.targetId()).isEqualTo(22L);
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
        when(rewards.selectById(7L)).thenReturn(reward(7L, RewardType.POINTS, 1, 31L, 50L));

        var view = service.update(2L, new UpdatePrizeCommand(" 新奖品 ", PrizeType.POINTS,
                PrizeLevel.SECOND, "  ", " 新说明 ", false));

        verify(mapper).updateById(row);
        assertThat(view.prizeName()).isEqualTo("新奖品");
        assertThat(view.imageUrl()).isNull();
        assertThat(view.description()).isEqualTo("新说明");
        assertThat(view.status()).isZero();
        assertThat(view.targetId()).isEqualTo(31L);
        assertThat(row.getRewardDefinitionId()).isEqualTo(7L);
    }

    @Test
    void updateRejectsPrizeTypeThatDoesNotMatchExistingRewardType() {
        PrizeRow row = prize(2L, 1, 7L);
        when(mapper.selectById(2L)).thenReturn(row);
        when(rewards.selectById(7L)).thenReturn(reward(7L, RewardType.COUPON, 1));
        error(() -> service.update(2L, new UpdatePrizeCommand("x", PrizeType.POINTS,
                PrizeLevel.SECOND, null, null, false)), PrizeErrorCode.REWARD_BINDING_INVALID);
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
        return reward(id, type, status, null, 1L);
    }

    private RewardDefinitionRow reward(long id, RewardType type, int status, Long targetId, Long quantity) {
        RewardDefinitionRow row = new RewardDefinitionRow();
        row.setId(id);
        row.setRewardType(type);
        row.setStatus(status);
        row.setTargetId(targetId);
        row.setQuantity(quantity);
        return row;
    }

    private void error(Runnable action, PrizeErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PrizeCatalogException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(expected);
            assertThat(error.getMessage()).isEqualTo(expected.message());
        });
    }
}
