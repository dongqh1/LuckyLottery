package com.dongqh.luckyhub.controller.drawchance;

import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.domain.model.auth.LoginPrincipal;
import com.dongqh.luckyhub.service.drawchance.DrawChanceService;
import com.dongqh.luckyhub.domain.vo.drawchance.DrawChanceAccountView;
import com.dongqh.luckyhub.domain.annotation.rbac.RequirePermission;
import com.dongqh.luckyhub.domain.constant.rbac.PermissionCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DrawChanceControllerTests {
    @AfterEach
    void clearLogin() {
        LoginContext.clear();
    }

    @Test
    void returnsOnlyCurrentUsersRemainingChances() throws Exception {
        DrawChanceService service = mock(DrawChanceService.class);
        when(service.get(77L)).thenReturn(new DrawChanceAccountView(77L, 15L, 0L));
        LoginContext.set(new LoginPrincipal(77L, "hmall:77", "session"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DrawChanceController(service)).build();

        mvc.perform(get("/api/lottery/chances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(77))
                .andExpect(jsonPath("$.data.availableBalance").value(15))
                .andExpect(jsonPath("$.data.reservedBalance").value(0));
        verify(service).get(77L);
        assertThat(DrawChanceController.class.getMethod("mine").getAnnotation(RequirePermission.class).value())
                .isEqualTo(PermissionCodes.LOTTERY_ACTIVITY_READ);
    }
}
