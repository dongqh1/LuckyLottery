package com.dongqh.membershipservice.domain;

import java.time.LocalDateTime;

public record MembershipTermSnapshot(
        String membershipLevel,
        LocalDateTime startsAt,
        LocalDateTime expiresAt
) {
}
