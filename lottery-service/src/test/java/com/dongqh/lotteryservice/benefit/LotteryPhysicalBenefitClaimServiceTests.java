package com.dongqh.lotteryservice.benefit;

import com.dongqh.hmapi.client.CouponClient;
import com.dongqh.hmapi.client.LogisticsClient;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.dto.CreateShippingOrderRequest;
import com.dongqh.hmapi.dto.ShippingOrderDTO;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.lotteryservice.domain.dto.benefit.ClaimPhysicalBenefitCommand;
import com.dongqh.lotteryservice.domain.po.draworder.DrawOrderRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.domain.po.prizecatalog.PrizeRow;
import com.dongqh.lotteryservice.domain.po.prizecatalog.RewardDefinitionRow;
import com.dongqh.lotteryservice.enums.draworder.DrawOrderStatus;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import com.dongqh.lotteryservice.enums.prizecatalog.RewardType;
import com.dongqh.lotteryservice.exception.LotteryDomainException;
import com.dongqh.lotteryservice.mapper.draworder.DrawOrderMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.UserBenefitMapper;
import com.dongqh.lotteryservice.mapper.prizecatalog.PrizeCatalogMapper;
import com.dongqh.lotteryservice.mapper.prizecatalog.RewardDefinitionMapper;
import com.dongqh.lotteryservice.reward.LotteryCurrentRewardResolver;
import com.dongqh.lotteryservice.service.benefit.LotteryPhysicalBenefitClaimService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LotteryPhysicalBenefitClaimServiceTests {
    @Test
    void createsShippingOrderFromCurrentProductData() {
        Fixture fixture = fixture(false, 4);
        ShippingOrderDTO created = shipping(501L);
        when(fixture.fulfillment.createShippingOrder(eq("Bearer test"),
                any(CreateShippingOrderRequest.class))).thenReturn(created);
        when(fixture.benefits.markClaimed(eq(41L), eq(501L), any(LocalDateTime.class))).thenReturn(1);

        ShippingOrderDTO result = fixture.service.claim(7L, 41L, "Bearer test",
                new ClaimPhysicalBenefitCommand("123e4567-e89b-12d3-a456-426614174000", 123L));

        assertThat(result).isEqualTo(created);
        var request = org.mockito.ArgumentCaptor.forClass(CreateShippingOrderRequest.class);
        verify(fixture.fulfillment).createShippingOrder(eq("Bearer test"), request.capture());
        assertThat(request.getValue().hmallItemId()).isEqualTo(91L);
        assertThat(request.getValue().skuCode()).isEqualTo("HMALL-ITEM-91");
        assertThat(request.getValue().productName()).isEqualTo("Current product name");
        assertThat(request.getValue().imageUrl()).isEqualTo("/current.png");
        assertThat(request.getValue().quantity()).isEqualTo(3);
        assertThat(request.getValue().sourceType()).isEqualTo("LOTTERY_BENEFIT");
        verify(fixture.benefits).markClaimed(eq(41L), eq(501L), any(LocalDateTime.class));
    }

    @Test
    void rejectsClaimWhenCurrentProductStockIsBelowRewardQuantity() {
        Fixture fixture = fixture(false, 2);

        assertThatThrownBy(() -> fixture.service.claim(7L, 41L, "Bearer test",
                new ClaimPhysicalBenefitCommand("123e4567-e89b-12d3-a456-426614174000", 123L)))
                .isInstanceOf(LotteryDomainException.class);

        verify(fixture.fulfillment, never()).createShippingOrder(any(), any());
        verify(fixture.benefits, never()).markClaimed(any(Long.class), any(Long.class), any(LocalDateTime.class));
    }

    @Test
    void repeatedClaimReturnsExistingShippingOrderWithoutResolvingChangedReward() {
        Fixture fixture = fixture(true);
        ShippingOrderDTO existing = shipping(501L);
        when(fixture.fulfillment.getShippingOrdersByIds(List.of(501L))).thenReturn(List.of(existing));

        ShippingOrderDTO result = fixture.service.claim(7L, 41L, "Bearer test",
                new ClaimPhysicalBenefitCommand("123e4567-e89b-12d3-a456-426614174000", 123L));

        assertThat(result).isEqualTo(existing);
        verify(fixture.prizes, never()).selectById(51L);
        verify(fixture.fulfillment, never()).createShippingOrder(any(), any());
        verify(fixture.benefits, never()).markClaimed(any(Long.class), any(Long.class), any(LocalDateTime.class));
    }

    private Fixture fixture(boolean alreadyClaimed) {
        return fixture(alreadyClaimed, 4);
    }

    private Fixture fixture(boolean alreadyClaimed, int itemStock) {
        UserBenefitMapper benefits = mock(UserBenefitMapper.class);
        DrawRecordMapper records = mock(DrawRecordMapper.class);
        DrawOrderMapper orders = mock(DrawOrderMapper.class);
        LogisticsClient fulfillment = mock(LogisticsClient.class);
        PrizeCatalogMapper prizes = mock(PrizeCatalogMapper.class);
        RewardDefinitionMapper definitions = mock(RewardDefinitionMapper.class);
        ItemClient items = mock(ItemClient.class);
        CouponClient coupons = mock(CouponClient.class);
        MembershipClient memberships = mock(MembershipClient.class);

        UserBenefitRow benefit = new UserBenefitRow();
        benefit.setId(41L);
        benefit.setDrawRecordId(31L);
        benefit.setUserId(7L);
        benefit.setPrizeId(51L);
        benefit.setStatus(alreadyClaimed ? BenefitStatus.FULFILLING : BenefitStatus.CLAIM_PENDING);
        benefit.setClaimDeadline(LocalDateTime.now().plusDays(1));
        if (alreadyClaimed) benefit.setShippingOrderId(501L);
        when(benefits.selectByIdForUpdate(41L)).thenReturn(benefit);

        DrawRecordRow record = new DrawRecordRow();
        record.setId(31L);
        record.setOrderId(11L);
        record.setUserId(7L);
        record.setPrizeId(51L);
        when(records.selectById(31L)).thenReturn(record);
        DrawOrderRow order = new DrawOrderRow();
        order.setId(11L);
        order.setUserId(7L);
        order.setStatus(DrawOrderStatus.SUCCESS);
        when(orders.selectById(11L)).thenReturn(order);

        PrizeRow prize = new PrizeRow();
        prize.setId(51L);
        prize.setPrizeType(PrizeType.PHYSICAL);
        prize.setRewardDefinitionId(61L);
        prize.setStatus(1);
        when(prizes.selectById(51L)).thenReturn(prize);
        RewardDefinitionRow definition = new RewardDefinitionRow();
        definition.setId(61L);
        definition.setRewardType(RewardType.PRODUCT);
        definition.setTargetId(91L);
        definition.setQuantity(3L);
        definition.setStatus(1);
        when(definitions.selectById(61L)).thenReturn(definition);
        ItemDTO item = new ItemDTO();
        item.setId(91L);
        item.setName("Current product name");
        item.setImage("/current.png");
        item.setStatus(1);
        item.setStock(itemStock);
        when(items.queryItemByIds(List.of(91L))).thenReturn(List.of(item));

        LotteryCurrentRewardResolver resolver = new LotteryCurrentRewardResolver(prizes, definitions,
                items, coupons, memberships);
        LotteryPhysicalBenefitClaimService service = new LotteryPhysicalBenefitClaimService(
                benefits, records, orders, fulfillment, resolver);
        return new Fixture(service, benefits, prizes, fulfillment);
    }

    private ShippingOrderDTO shipping(long id) {
        return new ShippingOrderDTO(id, "SHIP-" + id, "LOGISTICS-" + id, "PENDING",
                701L, "ADDRESS-SNAPSHOT-701", null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private record Fixture(LotteryPhysicalBenefitClaimService service, UserBenefitMapper benefits,
                           PrizeCatalogMapper prizes, LogisticsClient fulfillment) { }
}
