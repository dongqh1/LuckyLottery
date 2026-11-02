package com.dongqh.membership;

import com.dongqh.membership.controller.membership.InternalMembershipBenefitController;
import com.dongqh.membership.domain.model.membership.ActiveMembershipBenefit;
import com.dongqh.membership.service.membership.MembershipQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InternalMembershipBenefitControllerTests {
    private final MembershipQueryService memberships = mock(MembershipQueryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new InternalMembershipBenefitController(memberships)).build();
    }

    @Test
    void returnsDiscountAndMembershipSnapshotWithoutUnrelatedBenefits() throws Exception {
        when(memberships.activeBenefit(42L)).thenReturn(Optional.of(new ActiveMembershipBenefit(
                "GOLD", 9_000, 2, 12_000, LocalDateTime.now().plusDays(1))));

        mvc.perform(get("/internal/memberships/42/benefit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.discountBasisPoints").value(9_000))
                .andExpect(jsonPath("$.membershipLevel").value("GOLD"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.dailyDrawBonus").doesNotExist())
                .andExpect(jsonPath("$.pointsMultiplierBasisPoints").doesNotExist());
    }

    @Test
    void representsNoActiveMembershipExplicitly() throws Exception {
        when(memberships.activeBenefit(42L)).thenReturn(Optional.empty());

        mvc.perform(get("/internal/memberships/42/benefit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.discountBasisPoints").doesNotExist());
    }

    @Test
    void preservesMalformedActiveBenefitForTheConsumerToFailClosed() throws Exception {
        when(memberships.activeBenefit(42L)).thenReturn(Optional.of(new ActiveMembershipBenefit(
                "GOLD", null, 2, 12_000, LocalDateTime.now().plusDays(1))));

        mvc.perform(get("/internal/memberships/42/benefit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.discountBasisPoints").doesNotExist());
    }
}
