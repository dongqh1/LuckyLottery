package com.dongqh.membershipservice.domain;

import java.time.LocalDateTime;

public record MembershipGrantTerms(
        LocalDateTime accountStartsAt,
        LocalDateTime grantStartsAt,
        LocalDateTime expiresAt
) {
}
