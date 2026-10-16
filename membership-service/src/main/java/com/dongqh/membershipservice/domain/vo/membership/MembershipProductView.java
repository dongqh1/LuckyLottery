package com.dongqh.membershipservice.domain.vo.membership;

import com.dongqh.membershipservice.enums.membership.MembershipCardType;

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
