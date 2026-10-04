package com.dongqh.luckyhub.coupon.controller;

import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import com.dongqh.luckyhub.coupon.dto.CreateCouponTemplateCommand;
import com.dongqh.luckyhub.coupon.dto.IssueCouponCommand;
import com.dongqh.luckyhub.coupon.service.CouponService;
import com.dongqh.luckyhub.coupon.vo.CouponTemplateView;
import com.dongqh.luckyhub.coupon.vo.UserCouponView;
import com.hmall.common.domain.R;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class CouponAdminController {
    private final CouponService coupons;

    public CouponAdminController(CouponService coupons) {
        this.coupons = coupons;
    }

    @PostMapping("/coupon-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(ServicePermissionCodes.COUPON_MANAGE)
    public R<CouponTemplateView> createTemplate(@Valid @RequestBody CreateCouponTemplateCommand command) {
        return R.ok(coupons.createTemplate(command));
    }

    @PostMapping("/coupons/issues")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(ServicePermissionCodes.COUPON_MANAGE)
    public R<UserCouponView> issue(@Valid @RequestBody IssueCouponCommand command) {
        return R.ok(coupons.issue(command));
    }
}
