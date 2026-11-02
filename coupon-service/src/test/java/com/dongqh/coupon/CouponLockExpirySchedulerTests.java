package com.dongqh.coupon;

import com.dongqh.coupon.mapper.coupon.UserCouponMapper;
import com.dongqh.coupon.scheduler.coupon.CouponLockExpiryScheduler;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CouponLockExpirySchedulerTests {
    @Test
    void releasesLocksThatHaveBeenHeldForAtLeastThirtyMinutes() {
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        LocalDateTime before = LocalDateTime.now().minusMinutes(31);

        new CouponLockExpiryScheduler(coupons).releaseExpiredLocks();

        verify(coupons).releaseExpiredLocks(argThat(cutoff ->
                cutoff.isAfter(before) && cutoff.isBefore(LocalDateTime.now().minusMinutes(29))));
    }
}
