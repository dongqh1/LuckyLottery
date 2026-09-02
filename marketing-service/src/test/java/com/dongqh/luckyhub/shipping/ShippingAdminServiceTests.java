package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentStatus;
import com.dongqh.luckyhub.fulfillment.enums.FulfillmentType;
import com.dongqh.luckyhub.fulfillment.service.FulfillmentTaskService;
import com.dongqh.luckyhub.fulfillment.vo.FulfillmentTaskView;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.entity.ShippingOrder;
import com.dongqh.luckyhub.shipping.entity.ShippingTrackingEvent;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.enums.ShippingStatus;
import com.dongqh.luckyhub.shipping.enums.TrackingEventType;
import com.dongqh.luckyhub.shipping.mapper.ShippingAddressSnapshotMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingOrderMapper;
import com.dongqh.luckyhub.shipping.mapper.ShippingTrackingEventMapper;
import com.dongqh.luckyhub.shipping.service.ShippingOrderService;
import com.dongqh.luckyhub.shipping.service.ShippingProjectionWorker;
import com.dongqh.luckyhub.shipping.service.impl.ShippingAdminServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShippingAdminServiceTests {
    private final ShippingOrderMapper orders = mock(ShippingOrderMapper.class);
    private final ShippingAddressSnapshotMapper snapshots = mock(ShippingAddressSnapshotMapper.class);
    private final FulfillmentTaskService fulfillment = mock(FulfillmentTaskService.class);
    private final ShippingOrderService shipping = mock(ShippingOrderService.class);
    private final ShippingAdminServiceImpl service = new ShippingAdminServiceImpl(orders, snapshots, fulfillment, shipping);

    @Test
    void failedOrQuarantinedShipmentCanRetryAndReprojectWithoutChangingIdentity() {
        ShippingOrder failed = order(ShippingStatus.FAILED);
        when(orders.lockByShippingNo("SHIPPING-7")).thenReturn(failed);
        when(orders.selectByShippingNo("SHIPPING-7")).thenReturn(failed);
        when(snapshots.selectById(4L)).thenReturn(snapshot());
        when(fulfillment.get("LOGISTICS-7")).thenReturn(task(FulfillmentStatus.QUARANTINED));
        when(fulfillment.retryQuarantined(eq("LOGISTICS-7"), eq(77L), anyString()))
                .thenReturn(task(FulfillmentStatus.PENDING));

        String longSafeReason = "人工核验通过，确认可重试。".repeat(50);
        service.retry("SHIPPING-7", 77L, "  " + longSafeReason + "  ");

        verify(fulfillment).retryQuarantined("LOGISTICS-7", 77L, longSafeReason.substring(0, 500));
        verify(shipping).projectFulfillmentState("LOGISTICS-7");
    }

    @Test
    void safeOperationalReasonCanIncludeABoundedTicketReference() {
        ShippingOrder failed = order(ShippingStatus.FAILED);
        when(orders.lockByShippingNo("SHIPPING-7")).thenReturn(failed);
        when(orders.selectByShippingNo("SHIPPING-7")).thenReturn(failed);
        when(snapshots.selectById(4L)).thenReturn(snapshot());
        when(fulfillment.get("LOGISTICS-7")).thenReturn(task(FulfillmentStatus.QUARANTINED));

        service.retry("SHIPPING-7", 77L, "人工核验通过，确认可重试，工单 TASK-42");

        verify(fulfillment).retryQuarantined("LOGISTICS-7", 77L,
                "人工核验通过，确认可重试，工单 TASK-42");
    }

    @Test
    void rejectsChinesePiiStructuredTextAndSensitiveContentWithoutEchoingInput() {
        ShippingOrder failed = order(ShippingStatus.FAILED);
        when(orders.lockByShippingNo("SHIPPING-7")).thenReturn(failed);
        when(fulfillment.get("LOGISTICS-7")).thenReturn(task(FulfillmentStatus.QUARANTINED));
        String[] unsafeNotes = {
                "人工核验通过，收件人张三",
                "人工核验通过，手机号13800138000",
                "人工核验通过，身份证330106199001011234",
                "人工核验通过，座机0571-87654321",
                "人工核验通过，浙江省杭州市西湖区文三路90号",
                "{\"reason\":\"人工核验通过\"}",
                "人工核验通过\n确认可重试",
                "人工核验通过\u0001确认可重试",
                "人工核验通过，粘贴payload"
        };

        for (String unsafeNote : unsafeNotes) {
            assertThatThrownBy(() -> service.retry("SHIPPING-7", 77L, unsafeNote))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
                        assertThat(ex.getMessage()).doesNotContain(unsafeNote);
                    });
        }
        verify(fulfillment, never()).retryQuarantined(anyString(), anyLong(), any());
        verifyNoInteractions(shipping);
    }

    @Test
    void retryAndTerminateRejectUnsafeOrFinalShipmentStates() {
        for (ShippingStatus status : new ShippingStatus[]{ShippingStatus.FULFILLING, ShippingStatus.SHIPPED,
                ShippingStatus.IN_TRANSIT, ShippingStatus.DELIVERED, ShippingStatus.TERMINATED}) {
            ShippingOrder row = order(status);
            when(orders.lockByShippingNo("SHIPPING-7")).thenReturn(row);
            assertThatThrownBy(() -> service.retry("SHIPPING-7", 77L, "safe"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ShippingErrorCode.SHIPPING_STATE_CONFLICT));
        }
        for (ShippingStatus status : new ShippingStatus[]{ShippingStatus.SHIPPED,
                ShippingStatus.IN_TRANSIT, ShippingStatus.DELIVERED, ShippingStatus.TERMINATED}) {
            ShippingOrder row = order(status);
            when(orders.lockByShippingNo("SHIPPING-7")).thenReturn(row);
            assertThatThrownBy(() -> service.terminate("SHIPPING-7", 77L, "safe"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ShippingErrorCode.SHIPPING_STATE_CONFLICT));
        }
        verifyNoInteractions(fulfillment, shipping);
    }

    @Test
    void trackingProjectsOnlyFixedSafeTextAndPreservesMapperOrder() {
        ShippingTrackingEventMapper tracking = mock(ShippingTrackingEventMapper.class);
        ShippingAdminServiceImpl trackingService = new ShippingAdminServiceImpl(
                orders, snapshots, tracking, fulfillment, shipping, mock(ShippingProjectionWorker.class));
        ShippingOrder row = order(ShippingStatus.IN_TRANSIT);
        when(orders.selectByShippingNo("SHIPPING-7")).thenReturn(row);
        LocalDateTime firstTime = LocalDateTime.parse("2026-08-12T10:00:00");
        LocalDateTime secondTime = firstTime.plusHours(1);
        when(tracking.selectByShippingOrderId(7L)).thenReturn(List.of(
                trackingEvent(TrackingEventType.IN_TRANSIT, firstTime,
                        "张三 13800138000", "浙江省杭州市西湖区文三路90号"),
                trackingEvent(TrackingEventType.OUT_FOR_DELIVERY, secondTime,
                        "李四", "联系电话13900139000，送至教工路1号")));

        var result = trackingService.tracking("SHIPPING-7");

        assertThat(result).extracting(event -> event.eventType())
                .containsExactly(TrackingEventType.IN_TRANSIT, TrackingEventType.OUT_FOR_DELIVERY);
        assertThat(result).extracting(event -> event.eventTime())
                .containsExactly(firstTime, secondTime);
        assertThat(result).extracting(event -> event.locationSummary()).containsOnlyNulls();
        assertThat(result).extracting(event -> event.description())
                .containsExactly("包裹运输中", "包裹派送中");
        assertThat(result.toString()).doesNotContain(
                "张三", "李四", "13800138000", "13900139000", "文三路90号", "教工路1号");
    }

    @Test
    void trackingRejectsUnknownShippingOrderBeforeReadingEvents() {
        ShippingTrackingEventMapper tracking = mock(ShippingTrackingEventMapper.class);
        ShippingAdminServiceImpl trackingService = new ShippingAdminServiceImpl(
                orders, snapshots, tracking, fulfillment, shipping, mock(ShippingProjectionWorker.class));
        when(orders.selectByShippingNo("MISSING")).thenReturn(null);

        assertThatThrownBy(() -> trackingService.tracking("MISSING"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShippingErrorCode.SHIPPING_NOT_FOUND));
        verifyNoInteractions(tracking);
    }

    private ShippingOrder order(ShippingStatus status) {
        ShippingOrder order = new ShippingOrder();
        order.setId(7L); order.setShippingNo("SHIPPING-7"); order.setSourceType(ShippingSourceType.LOTTERY_BENEFIT);
        order.setSourceId("31"); order.setTargetUserId(9L); order.setAddressSnapshotId(4L);
        order.setSkuCode("SKU-7"); order.setProductName("礼盒"); order.setQuantity(1);
        order.setFulfillmentNo("LOGISTICS-7"); order.setStatus(status); order.setVersion(1);
        return order;
    }

    private ShippingAddressSnapshot snapshot() {
        ShippingAddressSnapshot value = new ShippingAddressSnapshot();
        value.setId(4L); value.setReceiverMasked("张*"); value.setPhoneMasked("138****5678");
        value.setRegionMasked("浙江省杭州市***"); return value;
    }

    private ShippingTrackingEvent trackingEvent(TrackingEventType type, LocalDateTime time,
                                                 String location, String description) {
        ShippingTrackingEvent event = new ShippingTrackingEvent();
        event.setEventType(type); event.setEventTime(time);
        event.setLocationSummary(location); event.setDescription(description);
        return event;
    }

    private FulfillmentTaskView task(FulfillmentStatus status) {
        return new FulfillmentTaskView(1L, "LOGISTICS-7", "LOTTERY_BENEFIT", "31", FulfillmentType.LOGISTICS,
                9L, null, "fingerprint", status, 5, 5, null, null, null, "SAFE", "安全摘要", null, null, null);
    }
}
