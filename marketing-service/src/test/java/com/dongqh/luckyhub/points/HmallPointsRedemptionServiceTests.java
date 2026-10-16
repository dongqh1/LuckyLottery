package com.dongqh.luckyhub.points;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dongqh.luckyhub.service.catalog.CatalogService;
import com.dongqh.luckyhub.domain.channel.inventory.service.ChannelInventoryService;
import com.dongqh.luckyhub.domain.dto.points.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.domain.po.points.PointsRedemptionOffer;
import com.dongqh.luckyhub.domain.po.points.PointsRedemptionOrder;
import com.dongqh.luckyhub.enums.points.PointsBusinessType;
import com.dongqh.luckyhub.enums.points.PointsRedemptionStatus;
import com.dongqh.luckyhub.mapper.points.PointsRedemptionOrderMapper;
import com.dongqh.luckyhub.service.points.PointsAccountService;
import com.dongqh.luckyhub.service.points.PointsRedemptionOfferService;
import com.dongqh.luckyhub.service.points.impl.PointsRedemptionServiceImpl;
import com.dongqh.luckyhub.domain.po.shipping.ShippingAddressSnapshot;
import com.dongqh.luckyhub.mapper.shipping.ShippingOrderMapper;
import com.dongqh.luckyhub.service.shipping.ShippingAddressSnapshotService;
import com.dongqh.luckyhub.service.shipping.ShippingOrderService;
import com.dongqh.luckyhub.domain.vo.shipping.ShippingOrderView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HmallPointsRedemptionServiceTests {
    @Mock private CatalogService catalog;
    @Mock private PointsAccountService accounts;
    @Mock private ChannelInventoryService inventory;
    @Mock private PointsRedemptionOrderMapper orders;
    @Mock private ShippingAddressSnapshotService snapshots;
    @Mock private ShippingOrderService shipping;
    @Mock private ShippingOrderMapper shippingOrders;
    @Mock private PointsRedemptionOfferService offers;

    @Test
    void redeemsHmallOfferWithImmutableSnapshotsPointsDebitAndFulfillment() {
        PointsRedemptionOffer offer = new PointsRedemptionOffer();
        offer.setId(71L);
        offer.setHmallItemId(317578L);
        offer.setUnitPoints(30L);
        offer.setAvailableStock(5);
        offer.setItemName("运动休闲鞋");
        offer.setImageUrl("/img/317578.jpg");
        offer.setCashPriceCent(9900);
        offer.setStatus(1);
        when(offers.requireAvailable(71L)).thenReturn(offer);

        AtomicReference<PointsRedemptionOrder> stored = new AtomicReference<>();
        when(orders.selectOne(any(Wrapper.class))).thenAnswer(invocation -> stored.get());
        when(orders.claim(any())).thenAnswer(invocation -> {
            PointsRedemptionOrder order = invocation.getArgument(0);
            order.setId(91L);
            stored.set(order);
            return 1;
        });
        when(orders.attachAddressSnapshot(91L, 33L)).thenReturn(1);
        when(orders.completeProcessing("HMALL-POINTS-1")).thenAnswer(invocation -> {
            stored.get().setStatus(PointsRedemptionStatus.COMPLETED);
            return 1;
        });
        when(orders.attachShippingOrder(91L, 501L)).thenAnswer(invocation -> {
            stored.get().setShippingOrderId(501L);
            return 1;
        });

        ShippingAddressSnapshot snapshot = new ShippingAddressSnapshot();
        snapshot.setId(33L);
        snapshot.setAddressId(12L);
        snapshot.setReceiverMasked("张*");
        snapshot.setPhoneMasked("138****1234");
        snapshot.setRegionMasked("北京市朝阳区");
        when(snapshots.create(anyLong(), anyLong(), any(), any())).thenReturn(snapshot);
        when(snapshots.require(33L)).thenReturn(snapshot);
        ShippingOrderView shippingView = mock(ShippingOrderView.class);
        when(shippingView.id()).thenReturn(501L);
        when(shipping.create(any())).thenReturn(shippingView);

        var service = new PointsRedemptionServiceImpl(catalog, accounts, inventory, orders,
                snapshots, shipping, shippingOrders, offers);
        var result = service.create(1L,
                new CreatePointsRedemptionCommand("HMALL-POINTS-1", null, 71L, 1, 12L));

        assertThat(result.offerId()).isEqualTo(71L);
        assertThat(result.hmallItemId()).isEqualTo(317578L);
        assertThat(result.productName()).isEqualTo("运动休闲鞋");
        assertThat(result.unitPoints()).isEqualTo(30L);
        assertThat(result.addressSnapshot()).isNotNull();
        assertThat(result.shippingOrderId()).isEqualTo(501L);

        verify(offers).deductStock(71L, 1);
        var debit = ArgumentCaptor.forClass(com.dongqh.luckyhub.domain.dto.points.PointsMutationCommand.class);
        verify(accounts).debit(debit.capture());
        assertThat(debit.getValue().businessType()).isEqualTo(PointsBusinessType.REDEMPTION);
        assertThat(debit.getValue().amount()).isEqualTo(30L);
        verify(shipping).create(argThat(command ->
                command.hmallItemId().equals(317578L)
                        && command.addressSnapshotId() == 33L
                        && command.quantity() == 1));
        verifyNoInteractions(catalog, inventory);
    }
}
