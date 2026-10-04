package com.hmall.common.utils;

import com.dongqh.hmsecurity.context.TrustedUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserContextBridgeTests {

    @BeforeEach
    @AfterEach
    void clearContexts() {
        UserContext.removeUser();
        TrustedUserContext.clear();
    }

    @Test
    void legacyFacadeWritesToSharedContext() {
        UserContext.setUser(17L);

        assertEquals(17L, TrustedUserContext.getUserId());
    }

    @Test
    void sharedContextIsReadableThroughLegacyFacade() {
        TrustedUserContext.setUserId(23L);

        assertEquals(23L, UserContext.getUser());
    }
}
