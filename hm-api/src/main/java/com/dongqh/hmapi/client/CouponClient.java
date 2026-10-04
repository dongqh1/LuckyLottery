package com.dongqh.hmapi.client;

import com.dongqh.hmapi.dto.CouponRewardTemplateDTO;
import com.dongqh.hmapi.dto.CouponRewardIssueRequest;
import com.dongqh.hmapi.dto.CouponOrderBindingRequest;
import com.dongqh.hmapi.dto.CouponReservationDTO;
import com.dongqh.hmapi.dto.ReserveCouponRequest;
import com.hmall.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("coupon-service")
public interface CouponClient {
    @GetMapping("/internal/coupons/templates/{templateId}/reward")
    CouponRewardTemplateDTO getRewardTemplate(@PathVariable("templateId") long templateId);

    @PostMapping("/internal/coupons/rewards/issue")
    void issueReward(@RequestBody CouponRewardIssueRequest request);

    @PostMapping("/api/coupons/checkout/reservations")
    R<CouponReservationDTO> reserveForCheckout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ReserveCouponRequest request);

    @PutMapping("/api/coupons/checkout/reservations/{couponId}/used")
    R<Void> useForCheckout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("couponId") long couponId,
            @RequestBody CouponOrderBindingRequest request);

    @DeleteMapping("/api/coupons/checkout/reservations/{couponId}")
    R<Void> releaseForCheckout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("couponId") long couponId,
            @RequestParam("orderNo") String orderNo);
}
