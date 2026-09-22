package com.dongqh.luckyhub.coupon.service;

import com.dongqh.luckyhub.coupon.dto.CouponCheckoutCommand;
import com.dongqh.luckyhub.coupon.dto.ReserveCouponCommand;
import com.dongqh.luckyhub.coupon.model.CouponLockSnapshot;
import com.dongqh.luckyhub.coupon.vo.ApplicableCouponView;

import java.util.List;

public interface CouponCheckoutService {
    List<ApplicableCouponView> listApplicable(long userId, CouponCheckoutCommand command);

    CouponLockSnapshot reserve(long userId, ReserveCouponCommand command);

    void use(long userId, long couponId, String orderNo);

    void release(long userId, long couponId, String orderNo);
}
