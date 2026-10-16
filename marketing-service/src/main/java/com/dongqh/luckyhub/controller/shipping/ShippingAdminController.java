package com.dongqh.luckyhub.controller.shipping;

import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.domain.dto.shipping.ShippingOperationCommand;
import com.dongqh.luckyhub.domain.dto.shipping.ShippingOrderQuery;
import com.dongqh.luckyhub.service.shipping.ShippingAdminService;
import com.dongqh.luckyhub.domain.vo.shipping.ShippingOrderView;
import com.dongqh.luckyhub.domain.vo.shipping.ShippingTrackingView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/shipping/orders")
public class ShippingAdminController {
    private final ShippingAdminService service;
    public ShippingAdminController(ShippingAdminService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResponse<ShippingOrderView>> page(@Valid @ModelAttribute ShippingOrderQuery query) {
        return ApiResponse.success(service.page(query));
    }
    @GetMapping("/{shippingNo}")
    public ApiResponse<ShippingOrderView> get(@PathVariable String shippingNo) {
        return ApiResponse.success(service.get(shippingNo));
    }
    @GetMapping("/{shippingNo}/tracking")
    public ApiResponse<java.util.List<ShippingTrackingView.TrackingEvent>> tracking(
            @PathVariable String shippingNo) {
        return ApiResponse.success(service.tracking(shippingNo));
    }
    @PostMapping("/{shippingNo}/retry")
    public ApiResponse<ShippingOrderView> retry(@PathVariable String shippingNo,
                                                @RequestBody(required = false) ShippingOperationCommand command) {
        return ApiResponse.success(service.retry(shippingNo, LoginContext.require().userId(),
                command == null ? null : command.note()));
    }
    @PostMapping("/{shippingNo}/terminate")
    public ApiResponse<ShippingOrderView> terminate(@PathVariable String shippingNo,
                                                    @RequestBody(required = false) ShippingOperationCommand command) {
        return ApiResponse.success(service.terminate(shippingNo, LoginContext.require().userId(),
                command == null ? null : command.note()));
    }
}

