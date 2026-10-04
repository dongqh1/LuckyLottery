package com.dongqh.fulfillmentservice.shipping.persistence;

import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import com.dongqh.fulfillmentservice.shipping.enums.ShippingStatus;
import java.util.List;

import java.time.LocalDateTime;

public interface LogisticsCallbackRepository {
    void insertReceipt(ShippingCallbackReceipt receipt);

    ShippingCallbackReceipt selectReceipt(String callbackId);

    ShippingCallbackOrder lockOrderByWaybillNo(String waybillNo);

    boolean insertTrackingEvent(ShippingTrackingEvent event);

    List<ShippingTrackingEvent> findTrackingByShippingOrderId(long shippingOrderId);

    boolean advanceOrderStatus(ShippingCallbackOrder order, ShippingStatus target, LocalDateTime deliveredAt);

    void markReceiptProcessed(String callbackId, LocalDateTime processedAt);

    void markReceiptRejected(String callbackId, String errorCode, String errorMessage, LocalDateTime processedAt);

    void appendStatusChanged(FulfillmentStatusChangedEventV1 event);
}
