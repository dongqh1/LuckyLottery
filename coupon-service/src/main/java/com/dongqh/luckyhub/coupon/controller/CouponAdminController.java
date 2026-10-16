package com.dongqh.luckyhub.controller.coupon;

import com.dongqh.luckyhub.domain.dto.coupon.CreateCouponTemplateCommand;
import com.dongqh.luckyhub.domain.dto.coupon.IssueCouponCommand;
import com.dongqh.luckyhub.service.coupon.CouponService;
import com.dongqh.luckyhub.domain.vo.coupon.CouponTemplateView;
import com.dongqh.luckyhub.domain.vo.coupon.UserCouponView;
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
    public R<CouponTemplateView> createTemplate(@Valid @RequestBody CreateCouponTemplateCommand command) {
        return R.ok(coupons.createTemplate(command));
    }

    @PostMapping("/coupons/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public R<UserCouponView> issue(@Valid @RequestBody IssueCouponCommand command) {
        return R.ok(coupons.issue(command));
    }
}

