package com.dongqh.luckyhub.coupon.controller;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.coupon.dto.CouponCheckoutCommand;
import com.dongqh.luckyhub.coupon.dto.CouponOrderBindingCommand;
import com.dongqh.luckyhub.coupon.dto.ReserveCouponCommand;
import com.dongqh.luckyhub.coupon.model.CouponLockSnapshot;
import com.dongqh.luckyhub.coupon.service.CouponCheckoutService;
import com.dongqh.luckyhub.coupon.vo.ApplicableCouponView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/coupons/checkout")
@RequirePermission(PermissionCodes.COUPON_READ)
public class CouponCheckoutController {
    private final CouponCheckoutService service;

    public CouponCheckoutController(CouponCheckoutService service) {
        this.service = service;
    }

    @PostMapping("/applicable")
    public ApiResponse<List<ApplicableCouponView>> listApplicable(
            @Valid @RequestBody CouponCheckoutCommand command) {
        return ApiResponse.success(service.listApplicable(LoginContext.require().userId(), command));
    }

    @PostMapping("/reservations")
    public ApiResponse<CouponLockSnapshot> reserve(
            @Valid @RequestBody ReserveCouponCommand command) {
        return ApiResponse.success(service.reserve(LoginContext.require().userId(), command));
    }

    @PutMapping("/reservations/{couponId}/used")
    public ApiResponse<Void> use(@PathVariable long couponId,
                                 @Valid @RequestBody CouponOrderBindingCommand command) {
        service.use(LoginContext.require().userId(), couponId, command.orderNo());
        return ApiResponse.success();
    }

    @DeleteMapping("/reservations/{couponId}")
    public ApiResponse<Void> release(@PathVariable long couponId,
                                     @RequestParam String orderNo) {
        service.release(LoginContext.require().userId(), couponId, orderNo);
        return ApiResponse.success();
    }
}
