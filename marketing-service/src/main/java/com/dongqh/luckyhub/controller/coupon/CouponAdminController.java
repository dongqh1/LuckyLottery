package com.dongqh.luckyhub.controller.coupon;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.domain.dto.coupon.CreateCouponTemplateCommand;
import com.dongqh.luckyhub.domain.dto.coupon.IssueCouponCommand;
import com.dongqh.luckyhub.service.coupon.CouponService;
import com.dongqh.luckyhub.domain.vo.coupon.CouponTemplateView;
import com.dongqh.luckyhub.domain.vo.coupon.UserCouponView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class CouponAdminController {
    private final CouponService service;
    public CouponAdminController(CouponService service) { this.service = service; }

    @PostMapping("/coupon-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponTemplateView> createTemplate(@Valid @RequestBody CreateCouponTemplateCommand command) {
        return ApiResponse.success(service.createTemplate(command));
    }

    @PostMapping("/coupons/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserCouponView> issue(@Valid @RequestBody IssueCouponCommand command) {
        return ApiResponse.success(service.issue(command));
    }
}

