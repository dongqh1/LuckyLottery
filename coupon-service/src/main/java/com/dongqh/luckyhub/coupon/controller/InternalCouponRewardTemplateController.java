package com.dongqh.luckyhub.coupon.controller;

import com.dongqh.hmapi.dto.CouponRewardTemplateDTO;
import com.dongqh.luckyhub.coupon.service.CouponRewardTemplateQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/coupons/templates")
public class InternalCouponRewardTemplateController {
    private final CouponRewardTemplateQueryService templates;

    public InternalCouponRewardTemplateController(CouponRewardTemplateQueryService templates) {
        this.templates = templates;
    }

    @GetMapping("/{templateId}/reward")
    public CouponRewardTemplateDTO getRewardTemplate(@PathVariable("templateId") long templateId) {
        return templates.getEnabled(templateId);
    }
}
