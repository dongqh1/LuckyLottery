package com.dongqh.luckyhub.membership;

import com.dongqh.luckyhub.membership.controller.MembershipController;
import com.dongqh.luckyhub.membership.enums.MembershipCardType;
import com.dongqh.luckyhub.membership.service.MembershipService;
import com.dongqh.luckyhub.membership.vo.MembershipProductView;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MembershipControllerTests {
    @Test
    void exposesEnabledMembershipProducts() throws Exception {
        MembershipService service = mock(MembershipService.class);
        when(service.listEnabledProducts()).thenReturn(List.of(
                new MembershipProductView(3L, "TRIAL", "体验卡", "MEMBER",
                        MembershipCardType.MONTH, 30, 0L, 10_000, 0, 10_000, 1)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MembershipController(service)).build();

        mvc.perform(get("/api/memberships/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productCode").value("TRIAL"))
                .andExpect(jsonPath("$.data[0].status").value(1));
    }
}
