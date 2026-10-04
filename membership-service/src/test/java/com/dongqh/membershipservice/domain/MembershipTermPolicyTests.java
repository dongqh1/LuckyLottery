package com.dongqh.membershipservice.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipTermPolicyTests {

    private final MembershipTermPolicy policy = new MembershipTermPolicy();
    private final LocalDateTime now = LocalDateTime.parse("2026-09-25T10:00:00.123");

    @Test
    void sameLevelActiveRenewalExtendsFromExpiryAndKeepsAccountStart() {
        LocalDateTime originalStart = now.minusDays(10);
        LocalDateTime existingExpiry = now.plusDays(5);

        MembershipGrantTerms terms = policy.resolve(
                new MembershipTermSnapshot("GOLD", originalStart, existingExpiry),
                "GOLD", now, 30);

        assertThat(terms.accountStartsAt()).isEqualTo(originalStart);
        assertThat(terms.grantStartsAt()).isEqualTo(existingExpiry);
        assertThat(terms.expiresAt()).isEqualTo(existingExpiry.plusDays(30));
    }

    @Test
    void expiredOrDifferentLevelStartsANewTermAtNow() {
        MembershipGrantTerms expired = policy.resolve(
                new MembershipTermSnapshot("GOLD", now.minusDays(60), now.minusNanos(1)),
                "GOLD", now, 30);
        MembershipGrantTerms levelChange = policy.resolve(
                new MembershipTermSnapshot("GOLD", now.minusDays(10), now.plusDays(5)),
                "PLATINUM", now, 90);

        assertThat(expired.accountStartsAt()).isEqualTo(now);
        assertThat(expired.grantStartsAt()).isEqualTo(now);
        assertThat(expired.expiresAt()).isEqualTo(now.plusDays(30));
        assertThat(levelChange.accountStartsAt()).isEqualTo(now);
        assertThat(levelChange.grantStartsAt()).isEqualTo(now);
        assertThat(levelChange.expiresAt()).isEqualTo(now.plusDays(90));
    }

    @Test
    void firstGrantStartsAccountAndGrantTermsAtNow() {
        MembershipGrantTerms terms = policy.resolve(null, "GOLD", now, 365);

        assertThat(terms.accountStartsAt()).isEqualTo(now);
        assertThat(terms.grantStartsAt()).isEqualTo(now);
        assertThat(terms.expiresAt()).isEqualTo(now.plusDays(365));
    }
}
