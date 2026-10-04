package com.dongqh.luckyhub.coupon.scheduler;

import com.dongqh.luckyhub.coupon.mapper.UserCouponMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CouponLockExpiryScheduler {
    private final UserCouponMapper coupons;

    public CouponLockExpiryScheduler(UserCouponMapper coupons) {
        this.coupons = coupons;
    }

    @Scheduled(
            fixedDelayString = "${luckyhub.coupon.lock-expiry-interval:60s}",
            initialDelayString = "${luckyhub.coupon.lock-expiry-initial-delay:60s}"
    )
    public void releaseExpiredLocks() {
        coupons.releaseExpiredLocks(LocalDateTime.now().minusMinutes(30));
    }
}
