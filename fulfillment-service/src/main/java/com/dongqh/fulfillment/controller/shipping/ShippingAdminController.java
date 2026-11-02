package com.dongqh.fulfillment.controller.shipping;

import com.dongqh.fulfillment.domain.model.FulfillmentPage;
import com.dongqh.fulfillment.domain.shipping.ShippingDomainException;
import com.dongqh.fulfillment.domain.shipping.ShippingErrorCode;
import com.dongqh.fulfillment.domain.model.shipping.ShippingOrderAdminQuery;
import com.dongqh.fulfillment.domain.model.shipping.ShippingOrderView;
import com.dongqh.fulfillment.domain.model.shipping.ShippingTrackingView;
import com.dongqh.fulfillment.domain.model.shipping.AddShippingTrackingEventCommand;
import com.dongqh.fulfillment.domain.model.shipping.UpdateShippingLogisticsCommand;
import com.dongqh.fulfillment.service.shipping.ShippingAdminService;
import com.hmall.common.domain.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shipping/orders")
public class ShippingAdminController {
    private final ShippingAdminService shipping;
    private final LogisticsAdminAuthorization authorization;

    public ShippingAdminController(ShippingAdminService shipping, LogisticsAdminAuthorization authorization) {
        this.shipping = shipping;
        this.authorization = authorization;
    }

    @GetMapping
    public R<FulfillmentPage<ShippingOrderView>> page(@ModelAttribute ShippingOrderAdminQuery query) {
        authorization.requireAdminId();
        return R.ok(shipping.page(query));
    }

    @GetMapping("/{shippingNo}")
    public R<ShippingOrderView> get(@PathVariable("shippingNo") String shippingNo) {
        authorization.requireAdminId();
        return R.ok(shipping.get(shippingNo));
    }

    @GetMapping("/{shippingNo}/tracking")
    public R<List<ShippingTrackingView.TrackingEvent>> tracking(@PathVariable("shippingNo") String shippingNo) {
        authorization.requireAdminId();
        return R.ok(shipping.tracking(shippingNo).tracking());
    }

    @PutMapping("/{shippingNo}/logistics")
    public R<ShippingOrderView> updateLogistics(@PathVariable("shippingNo") String shippingNo,
                                               @RequestBody UpdateShippingLogisticsCommand command) {
        return R.ok(shipping.updateLogistics(shippingNo, authorization.requireAdminId(), command));
    }

    @PostMapping("/{shippingNo}/tracking")
    public R<ShippingOrderView> addTrackingEvent(@PathVariable("shippingNo") String shippingNo,
                                                @RequestBody AddShippingTrackingEventCommand command) {
        return R.ok(shipping.addTrackingEvent(shippingNo, authorization.requireAdminId(), command));
    }

    @PostMapping("/{shippingNo}/retry")
    public R<ShippingOrderView> retry(@PathVariable("shippingNo") String shippingNo,
                                      @RequestBody(required = false) OperationRequest command) {
        return R.ok(shipping.retry(shippingNo, authorization.requireAdminId(), command == null ? null : command.note()));
    }

    @PostMapping("/{shippingNo}/terminate")
    public R<ShippingOrderView> terminate(@PathVariable("shippingNo") String shippingNo,
                                          @RequestBody(required = false) OperationRequest command) {
        return R.ok(shipping.terminate(shippingNo, authorization.requireAdminId(), command == null ? null : command.note()));
    }

    @ExceptionHandler(ShippingDomainException.class)
    public ResponseEntity<R<Void>> handleShippingError(ShippingDomainException exception) {
        ShippingErrorCode error = exception.errorCode();
        HttpStatus status = switch (error) {
            case ADDRESS_NOT_FOUND, SHIPPING_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ADDRESS_INVALID, SHIPPING_REQUEST_INVALID -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
        int code = switch (error) {
            case SHIPPING_NOT_FOUND -> 55005;
            case SHIPPING_IDEMPOTENCY_CONFLICT -> 55006;
            case SHIPPING_STATE_CONFLICT -> 55007;
            case ADDRESS_NOT_FOUND -> 55010;
            case ADDRESS_INVALID -> 55011;
            default -> 55012;
        };
        String message = switch (error) {
            case SHIPPING_NOT_FOUND -> "发货单不存在";
            case SHIPPING_IDEMPOTENCY_CONFLICT -> "发货请求幂等参数冲突";
            case SHIPPING_STATE_CONFLICT -> "发货单状态冲突";
            case ADDRESS_NOT_FOUND -> "收货地址不存在";
            case ADDRESS_INVALID -> "收货地址信息无效";
            default -> "物流请求参数不合法";
        };
        return ResponseEntity.status(status).body(R.error(code, message));
    }

    public record OperationRequest(String note) { }
}

