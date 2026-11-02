package com.dongqh.fulfillment.controller.shipping;

import com.dongqh.fulfillment.domain.shipping.ShippingDomainException;
import com.dongqh.fulfillment.domain.shipping.ShippingErrorCode;
import com.dongqh.fulfillment.domain.model.shipping.ShippingTrackingView;
import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillment.service.shipping.ShippingTrackingQueryService;
import com.hmall.common.domain.R;
import com.hmall.common.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/shipping/orders")
public class ShippingQueryController {
    private final ShippingTrackingQueryService tracking;

    public ShippingQueryController(ShippingTrackingQueryService tracking) {
        this.tracking = tracking;
    }

    @GetMapping("/{shippingNo}")
    public R<ShippingTrackingView> get(@PathVariable("shippingNo") String shippingNo) {
        return R.ok(tracking.getForUser(requireUserId(), shippingNo));
    }

    @GetMapping("/by-source")
    public R<ShippingTrackingView> getBySource(
            @RequestParam("sourceType") ShippingSourceType sourceType,
            @RequestParam("sourceId") String sourceId) {
        return R.ok(tracking.getForUserBySource(requireUserId(), sourceType, sourceId));
    }

    @GetMapping("/{shippingNo}/tracking")
    public R<List<ShippingTrackingView.TrackingEvent>> events(
            @PathVariable("shippingNo") String shippingNo) {
        return R.ok(tracking.getForUser(requireUserId(), shippingNo).tracking());
    }

    @ExceptionHandler(ShippingDomainException.class)
    public ResponseEntity<R<Void>> handleShippingError(ShippingDomainException exception) {
        ShippingErrorCode error = exception.errorCode();
        HttpStatus status = error == ShippingErrorCode.SHIPPING_NOT_FOUND
                ? HttpStatus.NOT_FOUND : error == ShippingErrorCode.SHIPPING_REQUEST_INVALID
                ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        int code = switch (error) {
            case SHIPPING_NOT_FOUND -> 55005;
            case SHIPPING_IDEMPOTENCY_CONFLICT -> 55006;
            case SHIPPING_STATE_CONFLICT -> 55007;
            default -> 55012;
        };
        String message = switch (error) {
            case SHIPPING_NOT_FOUND -> "发货单不存在";
            case SHIPPING_IDEMPOTENCY_CONFLICT -> "发货请求幂等参数冲突";
            case SHIPPING_STATE_CONFLICT -> "发货单状态冲突";
            default -> "物流请求参数不合法";
        };
        return ResponseEntity.status(status).body(R.error(code, message));
    }

    private long requireUserId() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        return userId;
    }
}

