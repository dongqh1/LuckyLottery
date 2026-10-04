package com.dongqh.pointsservice.integration;

import com.dongqh.hmapi.client.UserClient;
import com.dongqh.luckyhub.points.service.port.PointsUserEligibilityPort;
import org.springframework.stereotype.Component;

@Component
public class UserClientPointsEligibilityAdapter implements PointsUserEligibilityPort {

    private final UserClient userClient;

    public UserClientPointsEligibilityAdapter(UserClient userClient) {
        this.userClient = userClient;
    }

    @Override
    public boolean isEnabled(long userId) {
        return Boolean.TRUE.equals(userClient.isEnabled(userId));
    }
}
