package com.dongqh.couponservice.integration;

import com.dongqh.hmapi.client.UserClient;
import com.dongqh.luckyhub.coupon.service.port.CouponUserEligibilityPort;
import org.springframework.stereotype.Component;

@Component
public class UserClientCouponEligibilityAdapter implements CouponUserEligibilityPort {
    private final UserClient users;

    public UserClientCouponEligibilityAdapter(UserClient users) {
        this.users = users;
    }

    @Override
    public boolean isEnabled(long userId) {
        return Boolean.TRUE.equals(users.isEnabled(userId));
    }
}
