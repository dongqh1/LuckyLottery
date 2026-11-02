package com.dongqh.coupon.controller;

import com.dongqh.coupon.domain.dto.coupon.CreateCouponTemplateCommand;
import com.dongqh.coupon.domain.dto.coupon.IssueCouponCommand;
import com.dongqh.coupon.service.coupon.CouponService;
import com.dongqh.coupon.domain.vo.coupon.CouponTemplateView;
import com.dongqh.coupon.domain.vo.coupon.UserCouponView;
import com.hmall.common.domain.R;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class CouponAdminController {
    private final CouponService coupons;

    public CouponAdminController(CouponService coupons) {
        this.coupons = coupons;
    }

    /*
        根据关键词查询所有优惠券
     */
    @GetMapping("/coupon-templates")
    public R<List<CouponTemplateView>> searchTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(coupons.searchTemplates(keyword, size));
    }

    /**
     * 创建优惠券
     * @param command
     * @return
     */
    @PostMapping("/coupon-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public R<CouponTemplateView> createTemplate(@Valid @RequestBody CreateCouponTemplateCommand command) {
        return R.ok(coupons.createTemplate(command));
    }

    /**
     * 发券
     * @param command
     * @return
     */
    @PostMapping("/coupons/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public R<UserCouponView> issue(@Valid @RequestBody IssueCouponCommand command) {
        return R.ok(coupons.issue(command));
    }
}

