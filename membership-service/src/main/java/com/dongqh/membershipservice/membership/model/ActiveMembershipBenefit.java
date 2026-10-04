package com.dongqh.membershipservice.membership.model;

import java.time.LocalDateTime;

public record ActiveMembershipBenefit(
        String membershipLevel,
        Integer discountBasisPoints,
        Integer dailyDrawBonus,
        Integer pointsMultiplierBasisPoints,
        LocalDateTime expiresAt
) {
}
