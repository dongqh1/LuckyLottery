package com.dongqh.couponservice.integration;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmapi.dto.MembershipBenefitDTO;
import com.dongqh.luckyhub.coupon.integration.CouponIntegrationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponUserMembershipIntegrationTests {
    @Test
    void mapsUserEligibilityAndTreatsAnEmptyResponseAsDisabled() {
        UserClient users = mock(UserClient.class);
        when(users.isEnabled(42L)).thenReturn(true);
        when(users.isEnabled(43L)).thenReturn(false);
        when(users.isEnabled(44L)).thenReturn(null);

        CouponIntegrationService integration = integration(mock(ItemClient.class), mock(MembershipClient.class), users);

        assertThat(integration.isEnabledUser(42L)).isTrue();
        assertThat(integration.isEnabledUser(43L)).isFalse();
        assertThat(integration.isEnabledUser(44L)).isFalse();
    }

    @Test
    void propagatesUserServiceFailures() {
        UserClient users = mock(UserClient.class);
        IllegalStateException failure = new IllegalStateException("user service unavailable");
        when(users.isEnabled(42L)).thenThrow(failure);

        assertThatThrownBy(() -> integration(mock(ItemClient.class), mock(MembershipClient.class), users)
                .isEnabledUser(42L)).isSameAs(failure);
    }

    @Test
    void mapsInactiveAndValidActiveMembershipBenefits() {
        MembershipClient memberships = mock(MembershipClient.class);
        when(memberships.activeBenefit(42L)).thenReturn(new MembershipBenefitDTO(false, null));
        when(memberships.activeBenefit(43L)).thenReturn(new MembershipBenefitDTO(true, 9_000));
        CouponIntegrationService integration = integration(mock(ItemClient.class), memberships, mock(UserClient.class));

        assertThat(integration.activeMembershipBenefit(42L)).isEmpty();
        assertThat(integration.activeMembershipBenefit(43L)).get()
                .extracting("discountBasisPoints")
                .isEqualTo(9_000);
    }

    @Test
    void rejectsMalformedActiveMembershipBenefitsInsteadOfFallingBackToFullPrice() {
        MembershipClient memberships = mock(MembershipClient.class);
        when(memberships.activeBenefit(42L)).thenReturn(new MembershipBenefitDTO(true, null));
        when(memberships.activeBenefit(43L)).thenReturn(new MembershipBenefitDTO(true, 0));
        when(memberships.activeBenefit(44L)).thenReturn(new MembershipBenefitDTO(true, 10_001));
        CouponIntegrationService integration = integration(mock(ItemClient.class), memberships, mock(UserClient.class));

        assertThatThrownBy(() -> integration.activeMembershipBenefit(42L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> integration.activeMembershipBenefit(43L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> integration.activeMembershipBenefit(44L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnEmptyMembershipResponseAndPropagatesTransportFailures() {
        MembershipClient missingResponse = mock(MembershipClient.class);
        when(missingResponse.activeBenefit(42L)).thenReturn(null);
        assertThatThrownBy(() -> integration(mock(ItemClient.class), missingResponse, mock(UserClient.class))
                .activeMembershipBenefit(42L)).isInstanceOf(IllegalStateException.class);

        MembershipClient unavailable = mock(MembershipClient.class);
        IllegalStateException failure = new IllegalStateException("membership service unavailable");
        when(unavailable.activeBenefit(43L)).thenThrow(failure);
        assertThatThrownBy(() -> integration(mock(ItemClient.class), unavailable, mock(UserClient.class))
                .activeMembershipBenefit(43L)).isSameAs(failure);
    }

    private CouponIntegrationService integration(ItemClient items, MembershipClient memberships, UserClient users) {
        return new CouponIntegrationService(items, memberships, users);
    }
}
