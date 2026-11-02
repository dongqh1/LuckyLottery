package com.dongqh.user.controller;

import com.dongqh.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InternalUserEligibilityControllerTests {
    private final UserMapper users = mock(UserMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new InternalUserEligibilityController(users)).build();
    }

    @Test
    void returnsTrueOnlyForNormalUsers() throws Exception {
        when(users.selectStatusValueById(42L)).thenReturn(1);

        mvc.perform(get("/internal/users/42/enabled"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void returnsFalseForFrozenMissingAndUnknownStatuses() throws Exception {
        when(users.selectStatusValueById(43L)).thenReturn(2);
        when(users.selectStatusValueById(44L)).thenReturn(null);
        when(users.selectStatusValueById(45L)).thenReturn(77);

        mvc.perform(get("/internal/users/43/enabled"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
        mvc.perform(get("/internal/users/44/enabled"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
        mvc.perform(get("/internal/users/45/enabled"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }
}
