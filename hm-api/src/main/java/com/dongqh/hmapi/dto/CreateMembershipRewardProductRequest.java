package com.dongqh.hmapi.dto;

public record CreateMembershipRewardProductRequest(
        String productCode,
        String productName,
        String membershipLevel,
        Integer durationDays
) {
}
