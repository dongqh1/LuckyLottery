package com.dongqh.membershipservice.membership.vo;

import com.dongqh.membershipservice.membership.enums.MembershipCardType;

public record MembershipProductView(
        Long id,
        String productCode,
        String productName,
        String membershipLevel,
        MembershipCardType cardType,
        Integer durationDays,
        Long priceCent,
        Integer discountBasisPoints,
        Integer dailyDrawBonus,
        Integer pointsMultiplierBasisPoints,
        Integer status
) {
}
