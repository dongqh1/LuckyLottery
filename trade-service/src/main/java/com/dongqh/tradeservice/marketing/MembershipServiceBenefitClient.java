package com.dongqh.tradeservice.marketing;

import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.dto.MembershipBenefitDTO;
import com.hmall.common.utils.UserContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "trade.membership-benefit-service", name = "enabled", havingValue = "true")
public class MembershipServiceBenefitClient implements MembershipBenefitClient {
    private static final int FULL_PRICE_BASIS_POINTS = 10_000;

    private final MembershipClient memberships;

    public MembershipServiceBenefitClient(MembershipClient memberships) {
        this.memberships = memberships;
    }

    @Override
    public MembershipBenefitView getActiveBenefit() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) {
            throw new IllegalStateException("当前请求缺少可信用户身份");
        }
        MembershipBenefitDTO benefit = memberships.activeBenefit(userId);
        if (benefit == null || benefit.active() == null) {
            throw new IllegalStateException("会员服务返回了无效的权益响应");
        }
        if (!benefit.active() || benefit.expiresAt() == null
                || !benefit.expiresAt().isAfter(LocalDateTime.now())) {
            return new MembershipBenefitView(null, FULL_PRICE_BASIS_POINTS, null);
        }
        Integer basisPoints = benefit.discountBasisPoints();
        if (basisPoints == null || basisPoints <= 0 || basisPoints > FULL_PRICE_BASIS_POINTS) {
            throw new IllegalStateException("会员服务返回了无效的折扣参数");
        }
        return new MembershipBenefitView(benefit.membershipLevel(), basisPoints, benefit.expiresAt());
    }
}
