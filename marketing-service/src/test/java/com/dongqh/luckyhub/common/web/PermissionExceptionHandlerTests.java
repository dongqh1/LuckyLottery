package com.dongqh.luckyhub.common.web;

import com.dongqh.hmsecurity.permission.MissingTrustedUserException;
import com.dongqh.hmsecurity.permission.PermissionDeniedException;
import com.dongqh.hmsecurity.permission.PermissionLookupUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PermissionExceptionHandlerTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SecurityExceptionController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsMissingTrustedUserToUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get("/test/security/missing-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20000));
    }

    @Test
    void mapsMissingPermissionToForbiddenEnvelope() throws Exception {
        mockMvc.perform(get("/test/security/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void mapsPermissionLookupFailureToUnavailableEnvelope() throws Exception {
        mockMvc.perform(get("/test/security/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @RestController
    static class SecurityExceptionController {

        @GetMapping("/test/security/missing-user")
        void missingUser() {
            throw new MissingTrustedUserException();
        }

        @GetMapping("/test/security/denied")
        void denied() {
            throw new PermissionDeniedException("permission denied");
        }

        @GetMapping("/test/security/unavailable")
        void unavailable() {
            throw new PermissionLookupUnavailableException(new IllegalStateException("lookup failed"));
        }
    }
}
