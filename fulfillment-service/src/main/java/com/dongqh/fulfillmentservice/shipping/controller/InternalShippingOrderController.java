package com.dongqh.fulfillmentservice.shipping.controller;

import com.dongqh.fulfillmentservice.shipping.domain.ShippingDomainException;
import com.dongqh.fulfillmentservice.shipping.domain.ShippingErrorCode;
import com.dongqh.fulfillmentservice.shipping.enums.ShippingSourceType;
import com.dongqh.fulfillmentservice.shipping.model.CreateShippingOrderFromAddressCommand;
import com.dongqh.fulfillmentservice.shipping.model.ShippingOrderView;
import com.dongqh.fulfillmentservice.shipping.service.ShippingOrderService;
import com.dongqh.hmapi.dto.CreateFulfillmentShippingOrderRequest;
import com.dongqh.hmapi.dto.FulfillmentShippingOrderDTO;
import com.dongqh.hmapi.dto.InternalServiceErrorDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/internal/shipping/orders")
public class InternalShippingOrderController {

    private final ShippingOrderService shippingOrders;

    public InternalShippingOrderController(ShippingOrderService shippingOrders) {
        this.shippingOrders = shippingOrders;
    }

    @PostMapping
    public FulfillmentShippingOrderDTO create(@RequestBody CreateFulfillmentShippingOrderRequest request) {
        if (request == null || (!ShippingSourceType.POINTS_REDEMPTION.name().equals(request.sourceType())
                && !ShippingSourceType.LOTTERY_BENEFIT.name().equals(request.sourceType()))) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        ShippingSourceType sourceType = ShippingSourceType.valueOf(request.sourceType());
        try {
            ShippingOrderView created = shippingOrders.createFromAddress(new CreateShippingOrderFromAddressCommand(
                    sourceType, request.sourceId(), request.targetUserId(),
                    request.addressId(), request.hmallItemId(), request.skuCode(), request.productName(),
                    request.imageUrl(), request.quantity(), request.claimRequestId()));
            return view(created);
        } catch (IllegalArgumentException exception) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
    }

    @GetMapping
    public List<FulfillmentShippingOrderDTO> getByIds(@RequestParam("ids") Collection<Long> ids) {
        return shippingOrders.getByIds(ids).stream()
                .filter(order -> order.sourceType() == ShippingSourceType.POINTS_REDEMPTION
                        || order.sourceType() == ShippingSourceType.LOTTERY_BENEFIT)
                .map(InternalShippingOrderController::view)
                .toList();
    }

    @ExceptionHandler(ShippingDomainException.class)
    public ResponseEntity<InternalServiceErrorDTO> handleShippingError(ShippingDomainException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case ADDRESS_NOT_FOUND, SHIPPING_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ADDRESS_INVALID, SHIPPING_REQUEST_INVALID -> HttpStatus.BAD_REQUEST;
            case SHIPPING_IDEMPOTENCY_CONFLICT, SHIPPING_STATE_CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(new InternalServiceErrorDTO(exception.errorCode().name(), exception.errorCode().name()));
    }

    private static FulfillmentShippingOrderDTO view(ShippingOrderView order) {
        return new FulfillmentShippingOrderDTO(order.id(), order.shippingNo(), order.fulfillmentNo(),
                order.status() == null ? null : order.status().name(), order.addressSnapshotId(),
                order.addressSnapshotNo(), order.carrierCode(), order.carrierName(),
                order.waybillNo(), order.lastErrorCode(), order.lastErrorMessage(), order.shippedAt(),
                order.deliveredAt(), order.failedAt(), order.terminatedAt(), order.receiverMasked(),
                order.phoneMasked(), order.regionMasked());
    }

    private static ShippingDomainException error(ShippingErrorCode code) {
        return new ShippingDomainException(code);
    }
}
