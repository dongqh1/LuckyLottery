package com.dongqh.luckyhub.controller.shipping;

import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.service.shipping.ShippingQueryService;
import com.dongqh.luckyhub.domain.vo.shipping.ShippingTrackingView;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/shipping/orders")
public class ShippingQueryController {
    private final ShippingQueryService service;

    public ShippingQueryController(ShippingQueryService service) {
        this.service = service;
    }

    @GetMapping("/{shippingNo}")
    public ApiResponse<ShippingTrackingView> get(@PathVariable @Size(max = 64) String shippingNo) {
        return ApiResponse.success(service.getForUser(LoginContext.require().userId(), shippingNo));
    }

    @GetMapping("/{shippingNo}/tracking")
    public ApiResponse<List<ShippingTrackingView.TrackingEvent>> tracking(
            @PathVariable @Size(max = 64) String shippingNo
    ) {
        return ApiResponse.success(service.getForUser(LoginContext.require().userId(), shippingNo).tracking());
    }
}

