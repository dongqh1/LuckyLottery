package com.dongqh.points.integration;

import com.dongqh.hmapi.client.UserClient;
import com.dongqh.points.integration.PointsIntegrationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointsUserIntegrationTests {

    private final UserClient userClient = mock(UserClient.class);
    private final PointsIntegrationService integration = new PointsIntegrationService(null, userClient);

    @Test
    void returnsTrueOnlyWhenUserServiceConfirmsEnabled() {
        when(userClient.isEnabled(101L)).thenReturn(true);
        assertThat(integration.isEnabledUser(101L)).isTrue();
        verify(userClient).isEnabled(101L);

        when(userClient.isEnabled(102L)).thenReturn(false);
        assertThat(integration.isEnabledUser(102L)).isFalse();

        when(userClient.isEnabled(103L)).thenReturn(null);
        assertThat(integration.isEnabledUser(103L)).isFalse();
    }

    @Test
    void propagatesUserServiceTransportFailuresInsteadOfAllowingTheOperation() {
        RuntimeException failure = new RuntimeException("user service unavailable");
        when(userClient.isEnabled(101L)).thenThrow(failure);

        assertThatThrownBy(() -> integration.isEnabledUser(101L)).isSameAs(failure);
    }
}
