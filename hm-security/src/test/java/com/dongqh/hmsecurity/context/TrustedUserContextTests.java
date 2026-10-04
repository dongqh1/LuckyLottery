package com.dongqh.hmsecurity.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrustedUserContextTests {

    @BeforeEach
    @AfterEach
    void clearContext() {
        TrustedUserContext.clear();
    }

    @Test
    void returnsNullWhenNoUserIsSet() {
        assertNull(TrustedUserContext.getUserId());
    }

    @Test
    void returnsTheUserIdSetOnTheCurrentThread() {
        TrustedUserContext.setUserId(42L);

        assertEquals(42L, TrustedUserContext.getUserId());
    }

    @Test
    void clearRemovesTheCurrentThreadUserId() {
        TrustedUserContext.setUserId(42L);

        TrustedUserContext.clear();

        assertNull(TrustedUserContext.getUserId());
    }
}
