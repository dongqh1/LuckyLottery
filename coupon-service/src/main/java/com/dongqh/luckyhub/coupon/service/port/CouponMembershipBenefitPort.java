package com.dongqh.luckyhub.coupon.service.port;

import java.util.Optional;

/** Read-only membership boundary used by checkout price calculation. */
public interface CouponMembershipBenefitPort {
    Optional<CouponMembershipBenefit> activeBenefit(long userId);
}
