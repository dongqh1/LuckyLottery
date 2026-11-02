package com.dongqh.lottery.security;

import com.dongqh.lottery.security.LotteryAdminAuthorization;
import com.hmall.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LotteryAdminAuthorizationTests {
    @AfterEach
    void clearUser() {
        UserContext.removeUser();
    }

    @Test
    void allowsOnlyUsersIncludedInConfiguredAdministratorList() {
        LotteryAdminAuthorization authorization = new LotteryAdminAuthorization("1, 42");
        UserContext.setUser(42L);

        assertThat(authorization.requireAdmin()).isEqualTo(42L);
    }

    @Test
    void rejectsOrdinaryAndUnauthenticatedUsersWithForbiddenStatus() {
        LotteryAdminAuthorization authorization = new LotteryAdminAuthorization("1");
        UserContext.setUser(23L);

        assertForbidden(authorization);
        UserContext.removeUser();
        assertForbidden(authorization);
    }

    @Test
    void rejectsMalformedAdministratorConfiguration() {
        assertThatThrownBy(() -> new LotteryAdminAuthorization("1,not-a-user"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertForbidden(LotteryAdminAuthorization authorization) {
        assertThatThrownBy(authorization::requireAdmin)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
