package com.dongqh.hmapi.dto;

public record MembershipPurchaseSnapshotDTO(
        Long productId,
        String productCode,
        String productName,
        String membershipLevel,
        Integer durationDays,
        Long priceCent,
        Integer discountBasisPoints,
        Integer dailyDrawBonus,
        Integer pointsMultiplierBasisPoints
) {
    public MembershipPurchaseSnapshotDTO {
        if (productId == null || productId <= 0 || productCode == null || productCode.isBlank()
                || productName == null || productName.isBlank() || membershipLevel == null
                || membershipLevel.isBlank() || durationDays == null || durationDays <= 0
                || priceCent == null || priceCent <= 0 || discountBasisPoints == null
                || discountBasisPoints < 1 || discountBasisPoints > 10_000
                || dailyDrawBonus == null || dailyDrawBonus < 0
                || pointsMultiplierBasisPoints == null || pointsMultiplierBasisPoints < 10_000) {
            throw new IllegalArgumentException("membership purchase snapshot is invalid");
        }
    }
}
