package com.dongqh.coupon.service.coupon;

import com.dongqh.coupon.domain.dto.coupon.CouponCheckoutCommand;
import com.dongqh.coupon.domain.dto.coupon.ReserveCouponCommand;
import com.dongqh.coupon.domain.model.coupon.CouponLockSnapshot;
import com.dongqh.coupon.domain.vo.coupon.ApplicableCouponView;

import java.util.List;

public interface CouponCheckoutService {

    List<ApplicableCouponView> listApplicable(long userId, CouponCheckoutCommand command);

    CouponLockSnapshot reserve(long userId, ReserveCouponCommand command);

    void use(long userId, long couponId, String orderNo);

    void release(long userId, long couponId, String orderNo);
}
