package com.dongqh.couponservice.integration;

import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.dto.MembershipBenefitDTO;
import com.dongqh.luckyhub.coupon.service.port.CouponMembershipBenefit;
import com.dongqh.luckyhub.coupon.service.port.CouponMembershipBenefitPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MembershipClientCouponBenefitAdapter implements CouponMembershipBenefitPort {
    private static final int MIN_DISCOUNT_BASIS_POINTS = 1;
    private static final int MAX_DISCOUNT_BASIS_POINTS = 10_000;

    private final MembershipClient memberships;

    public MembershipClientCouponBenefitAdapter(MembershipClient memberships) {
        this.memberships = memberships;
    }

    @Override
    public Optional<CouponMembershipBenefit> activeBenefit(long userId) {
        MembershipBenefitDTO response = memberships.activeBenefit(userId);
        if (response == null) {
            throw new IllegalStateException("Membership service returned no benefit response");
        }
        if (!response.active()) {
            return Optional.empty();
        }
        Integer discountBasisPoints = response.discountBasisPoints();
        if (discountBasisPoints == null
                || discountBasisPoints < MIN_DISCOUNT_BASIS_POINTS
                || discountBasisPoints > MAX_DISCOUNT_BASIS_POINTS) {
            throw new IllegalStateException("Membership service returned an invalid active discount");
        }
        return Optional.of(new CouponMembershipBenefit(discountBasisPoints));
    }
}
