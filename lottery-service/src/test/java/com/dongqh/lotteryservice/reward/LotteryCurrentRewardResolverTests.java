package com.dongqh.lotteryservice.reward;

import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.dto.CouponRewardTemplateDTO;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.lotteryservice.domain.po.prizecatalog.PrizeRow;
import com.dongqh.lotteryservice.domain.po.prizecatalog.RewardDefinitionRow;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import com.dongqh.lotteryservice.mapper.prizecatalog.PrizeCatalogMapper;
import com.dongqh.lotteryservice.mapper.prizecatalog.RewardDefinitionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LotteryCurrentRewardResolverTests {
    @Test
    void resolvesCurrentPointsConfigurationFromCatalog() {
        Fixture fixture = fixture(RewardType.POINTS, PrizeType.POINTS, null, 250L);

        CurrentLotteryReward current = fixture.resolver.resolve(51L);

        assertThat(current.definition().getId()).isEqualTo(61L);
        assertThat(current.definition().getQuantity()).isEqualTo(250L);
        assertThat(current.item()).isNull();
        assertThat(current.couponTemplate()).isNull();
    }

    @Test
    void resolvesCurrentCouponTemplateAndQuantity() {
        Fixture fixture = fixture(RewardType.COUPON, PrizeType.COUPON, 71L, 2L);
        when(fixture.coupons.getRewardTemplate(71L))
                .thenReturn(new CouponRewardTemplateDTO(71L, "CURRENT-CODE", 1));

        CurrentLotteryReward current = fixture.resolver.resolve(51L);

        assertThat(current.couponTemplate().templateCode()).isEqualTo("CURRENT-CODE");
        assertThat(current.definition().getQuantity()).isEqualTo(2L);
    }

    @Test
    void resolvesCurrentMembershipProduct() {
        Fixture fixture = fixture(RewardType.MEMBERSHIP, PrizeType.MEMBERSHIP, 81L, 2L);
        when(fixture.memberships.getRewardProduct(81L))
                .thenReturn(new MembershipRewardProductDTO(81L, "CURRENT-MEMBER", "GOLD", 30, 1));

        CurrentLotteryReward current = fixture.resolver.resolve(51L);

        assertThat(current.membershipProduct().productCode()).isEqualTo("CURRENT-MEMBER");
        assertThat(current.membershipProduct().durationDays()).isEqualTo(30);
    }

    @Test
    void resolvesCurrentProductByPrizeRewardDefinition() {
        Fixture fixture = fixture(RewardType.PRODUCT, PrizeType.PHYSICAL, 91L, 3L);
        ItemDTO item = new ItemDTO();
        item.setId(91L);
        item.setName("Current product name");
        item.setImage("/current.png");
        item.setStatus(1);
        when(fixture.items.queryItemByIds(List.of(91L))).thenReturn(List.of(item));

        CurrentLotteryReward current = fixture.resolver.resolve(51L);

        assertThat(current.item().getName()).isEqualTo("Current product name");
        assertThat(current.definition().getQuantity()).isEqualTo(3L);
    }

    @Test
    void drawChanceRewardNeedsNoExternalTarget() {
        Fixture fixture = fixture(RewardType.DRAW_CHANCE, PrizeType.DRAW_CHANCE, null, 4L);

        CurrentLotteryReward current = fixture.resolver.resolve(51L);

        assertThat(current.definition().getQuantity()).isEqualTo(4L);
    }

    @Test
    void changedCurrentTargetIsUsedWhenDispatchResolves() {
        Fixture fixture = fixture(RewardType.COUPON, PrizeType.COUPON, 72L, 1L);
        when(fixture.coupons.getRewardTemplate(72L))
                .thenReturn(new CouponRewardTemplateDTO(72L, "UPDATED-TEMPLATE", 1));

        assertThat(fixture.resolver.resolve(51L).couponTemplate().templateCode())
                .isEqualTo("UPDATED-TEMPLATE");
    }

    @Test
    void missingDisabledOrIncompatibleDefinitionIsRejected() {
        Fixture missing = fixture(RewardType.POINTS, PrizeType.POINTS, null, 10L);
        when(missing.definitions.selectById(61L)).thenReturn(null);
        assertReason(missing, RewardQuarantineReason.CURRENT_REWARD_UNAVAILABLE);

        Fixture disabled = fixture(RewardType.POINTS, PrizeType.POINTS, null, 10L);
        disabled.definition.setStatus(0);
        assertReason(disabled, RewardQuarantineReason.CURRENT_REWARD_UNAVAILABLE);

        Fixture incompatible = fixture(RewardType.POINTS, PrizeType.COUPON, null, 10L);
        assertReason(incompatible, RewardQuarantineReason.REWARD_TYPE_MISMATCH);
    }

    private void assertReason(Fixture fixture, RewardQuarantineReason expected) {
        assertThatThrownBy(() -> fixture.resolver.resolve(51L))
                .isInstanceOfSatisfying(RewardIdentityMismatchException.class,
                        error -> assertThat(error.reason()).isEqualTo(expected));
    }

    private Fixture fixture(RewardType rewardType, PrizeType prizeType, Long targetId, long quantity) {
        PrizeCatalogMapper prizes = mock(PrizeCatalogMapper.class);
        RewardDefinitionMapper definitions = mock(RewardDefinitionMapper.class);
        ItemClient items = mock(ItemClient.class);
        CouponClient coupons = mock(CouponClient.class);
        MembershipClient memberships = mock(MembershipClient.class);

        PrizeRow prize = new PrizeRow();
        prize.setId(51L);
        prize.setPrizeType(prizeType);
        prize.setRewardDefinitionId(61L);
        prize.setStatus(1);
        RewardDefinitionRow definition = new RewardDefinitionRow();
        definition.setId(61L);
        definition.setRewardType(rewardType);
        definition.setTargetId(targetId);
        definition.setQuantity(quantity);
        definition.setStatus(1);
        when(prizes.selectById(51L)).thenReturn(prize);
        when(definitions.selectById(61L)).thenReturn(definition);
        return new Fixture(new LotteryCurrentRewardResolver(prizes, definitions, items, coupons, memberships),
                prizes, definitions, items, coupons, memberships, prize, definition);
    }

    private record Fixture(LotteryCurrentRewardResolver resolver, PrizeCatalogMapper prizes,
                           RewardDefinitionMapper definitions, ItemClient items, CouponClient coupons,
                           MembershipClient memberships, PrizeRow prize, RewardDefinitionRow definition) { }
}
