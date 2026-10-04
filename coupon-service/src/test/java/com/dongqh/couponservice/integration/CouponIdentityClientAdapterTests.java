package com.dongqh.couponservice.integration;

import com.dongqh.hmapi.client.MembershipClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmapi.dto.MembershipBenefitDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponIdentityClientAdapterTests {
    @Test
    void mapsUserEligibilityAndTreatsAnEmptyResponseAsDisabled() {
        UserClient users = mock(UserClient.class);
        when(users.isEnabled(42L)).thenReturn(true);
        when(users.isEnabled(43L)).thenReturn(false);
        when(users.isEnabled(44L)).thenReturn(null);

        UserClientCouponEligibilityAdapter adapter = new UserClientCouponEligibilityAdapter(users);

        assertThat(adapter.isEnabled(42L)).isTrue();
        assertThat(adapter.isEnabled(43L)).isFalse();
        assertThat(adapter.isEnabled(44L)).isFalse();
    }

    @Test
    void propagatesUserServiceFailures() {
        UserClient users = mock(UserClient.class);
        IllegalStateException failure = new IllegalStateException("user service unavailable");
        when(users.isEnabled(42L)).thenThrow(failure);

        assertThatThrownBy(() -> new UserClientCouponEligibilityAdapter(users).isEnabled(42L))
                .isSameAs(failure);
    }

    @Test
    void mapsInactiveAndValidActiveMembershipBenefits() {
        MembershipClient memberships = mock(MembershipClient.class);
        when(memberships.activeBenefit(42L)).thenReturn(new MembershipBenefitDTO(false, null));
        when(memberships.activeBenefit(43L)).thenReturn(new MembershipBenefitDTO(true, 9_000));

        MembershipClientCouponBenefitAdapter adapter = new MembershipClientCouponBenefitAdapter(memberships);

        assertThat(adapter.activeBenefit(42L)).isEmpty();
        assertThat(adapter.activeBenefit(43L)).get()
                .extracting("discountBasisPoints")
                .isEqualTo(9_000);
    }

    @Test
    void rejectsMalformedActiveMembershipBenefitsInsteadOfFallingBackToFullPrice() {
        MembershipClient memberships = mock(MembershipClient.class);
        when(memberships.activeBenefit(42L)).thenReturn(new MembershipBenefitDTO(true, null));
        when(memberships.activeBenefit(43L)).thenReturn(new MembershipBenefitDTO(true, 0));
        when(memberships.activeBenefit(44L)).thenReturn(new MembershipBenefitDTO(true, 10_001));
        MembershipClientCouponBenefitAdapter adapter = new MembershipClientCouponBenefitAdapter(memberships);

        assertThatThrownBy(() -> adapter.activeBenefit(42L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.activeBenefit(43L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.activeBenefit(44L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnEmptyMembershipResponseAndPropagatesTransportFailures() {
        MembershipClient missingResponse = mock(MembershipClient.class);
        when(missingResponse.activeBenefit(42L)).thenReturn(null);
        assertThatThrownBy(() -> new MembershipClientCouponBenefitAdapter(missingResponse).activeBenefit(42L))
                .isInstanceOf(IllegalStateException.class);

        MembershipClient unavailable = mock(MembershipClient.class);
        IllegalStateException failure = new IllegalStateException("membership service unavailable");
        when(unavailable.activeBenefit(43L)).thenThrow(failure);
        assertThatThrownBy(() -> new MembershipClientCouponBenefitAdapter(unavailable).activeBenefit(43L))
                .isSameAs(failure);
    }
}
