package com.dongqh.membershipservice.domain;

import java.time.LocalDateTime;

public class MembershipTermPolicy {

    public MembershipGrantTerms resolve(
            MembershipTermSnapshot current,
            String requestedMembershipLevel,
            LocalDateTime now,
            int durationDays
    ) {
        LocalDateTime accountStartsAt = now;
        LocalDateTime grantStartsAt = now;
        LocalDateTime base = now;

        if (current != null
                && current.membershipLevel().equals(requestedMembershipLevel)
                && current.expiresAt().isAfter(now)) {
            accountStartsAt = current.startsAt();
            grantStartsAt = current.expiresAt();
            base = current.expiresAt();
        }

        return new MembershipGrantTerms(accountStartsAt, grantStartsAt, base.plusDays(durationDays));
    }
}
