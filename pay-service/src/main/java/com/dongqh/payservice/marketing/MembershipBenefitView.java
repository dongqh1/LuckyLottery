package com.dongqh.payservice.marketing;

import java.time.LocalDateTime;

public class MembershipBenefitView {
    private final String membershipLevel;
    private final int discountBasisPoints;
    private final LocalDateTime expiresAt;

    public MembershipBenefitView(String membershipLevel, int discountBasisPoints, LocalDateTime expiresAt) {
        this.membershipLevel = membershipLevel;
        this.discountBasisPoints = discountBasisPoints;
        this.expiresAt = expiresAt;
    }

    public String getMembershipLevel() {
        return membershipLevel;
    }

    public int getDiscountBasisPoints() {
        return discountBasisPoints;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
