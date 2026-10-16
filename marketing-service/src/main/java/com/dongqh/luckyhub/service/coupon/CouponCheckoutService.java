package com.dongqh.luckyhub.service.coupon;

import com.dongqh.luckyhub.domain.dto.coupon.CouponCheckoutCommand;
import com.dongqh.luckyhub.domain.dto.coupon.ReserveCouponCommand;
import com.dongqh.luckyhub.domain.model.coupon.CouponLockSnapshot;
import com.dongqh.luckyhub.domain.vo.coupon.ApplicableCouponView;

import java.util.List;

public interface CouponCheckoutService {
    List<ApplicableCouponView> listApplicable(long userId, CouponCheckoutCommand command);

    CouponLockSnapshot reserve(long userId, ReserveCouponCommand command);

    void use(long userId, long couponId, String orderNo);

    void release(long userId, long couponId, String orderNo);
}
