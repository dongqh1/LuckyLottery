package com.dongqh.pointsservice.integration;

import com.dongqh.hmapi.client.UserClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserClientPointsEligibilityAdapterTests {

    private final UserClient userClient = mock(UserClient.class);
    private final UserClientPointsEligibilityAdapter adapter = new UserClientPointsEligibilityAdapter(userClient);

    @Test
    void returnsTrueOnlyWhenUserServiceConfirmsEnabled() {
        when(userClient.isEnabled(101L)).thenReturn(true);
        assertThat(adapter.isEnabled(101L)).isTrue();
        verify(userClient).isEnabled(101L);

        when(userClient.isEnabled(102L)).thenReturn(false);
        assertThat(adapter.isEnabled(102L)).isFalse();

        when(userClient.isEnabled(103L)).thenReturn(null);
        assertThat(adapter.isEnabled(103L)).isFalse();
    }

    @Test
    void propagatesUserServiceTransportFailuresInsteadOfAllowingTheOperation() {
        RuntimeException failure = new RuntimeException("user service unavailable");
        when(userClient.isEnabled(101L)).thenThrow(failure);

        assertThatThrownBy(() -> adapter.isEnabled(101L)).isSameAs(failure);
    }
}
