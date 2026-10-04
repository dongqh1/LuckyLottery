package com.dongqh.luckyhub.coupon.controller;

import com.dongqh.luckyhub.coupon.service.CouponService;
import com.dongqh.luckyhub.coupon.vo.CouponPageResponse;
import com.dongqh.luckyhub.coupon.vo.UserCouponView;
import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import com.hmall.common.domain.R;
import com.hmall.common.utils.UserContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/coupons")
@RequirePermission(ServicePermissionCodes.COUPON_READ)
public class CouponQueryController {
    private final CouponService coupons;

    public CouponQueryController(CouponService coupons) {
        this.coupons = coupons;
    }

    @GetMapping
    public R<CouponPageResponse<UserCouponView>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return R.ok(coupons.pageMine(requireUserId(), page, size));
    }

    @GetMapping("/{couponId}")
    public R<UserCouponView> get(@PathVariable("couponId") @Positive long couponId) {
        return R.ok(coupons.getMine(requireUserId(), couponId));
    }

    private long requireUserId() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return userId;
    }
}
