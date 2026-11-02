package com.dongqh.membership.domain.model.membership;

import java.time.LocalDateTime;

public record ActiveMembershipBenefit(
        String membershipLevel,
        Integer discountBasisPoints,
        Integer dailyDrawBonus,
        Integer pointsMultiplierBasisPoints,
        LocalDateTime expiresAt
) {
}
