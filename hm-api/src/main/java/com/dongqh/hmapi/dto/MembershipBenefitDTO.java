package com.dongqh.hmapi.dto;

import java.time.LocalDateTime;

public record MembershipBenefitDTO(
        Boolean active,
        Integer discountBasisPoints,
        String membershipLevel,
        LocalDateTime expiresAt
) {
    public MembershipBenefitDTO(boolean active, Integer discountBasisPoints) {
        this(active, discountBasisPoints, null, null);
    }
}
