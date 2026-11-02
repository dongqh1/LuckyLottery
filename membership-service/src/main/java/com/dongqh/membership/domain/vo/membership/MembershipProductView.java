package com.dongqh.membership.domain.vo.membership;

import com.dongqh.membership.enums.membership.MembershipCardType;

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
