package com.dongqh.coupon.controller.coupon;

import com.dongqh.hmapi.dto.CouponRewardIssueRequest;
import com.dongqh.coupon.service.coupon.CouponService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/coupons/rewards")
public class InternalCouponRewardIssueController {
    private final CouponService coupons;

    public InternalCouponRewardIssueController(CouponService coupons) {
        this.coupons = coupons;
    }

    @PostMapping("/issue")
    public void issue(@RequestBody CouponRewardIssueRequest request) {
        coupons.issueReward(request.fulfillmentNo(), request.targetUserId(), request.couponTemplateId(),
                request.couponTemplateCode(), request.quantity());
    }
}
