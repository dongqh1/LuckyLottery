package com.dongqh.fulfillmentservice.security;

import com.dongqh.fulfillmentservice.shipping.controller.LogisticsCallbackController;
import com.dongqh.fulfillmentservice.shipping.service.LogisticsCallbackService;
import com.dongqh.fulfillmentservice.FulfillmentPermissionMvcConfiguration;
import com.dongqh.hmsecurity.permission.PermissionLookup;
import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.http.MediaType;
import com.dongqh.hmsecurity.context.TrustedUserContext;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = FulfillmentCallbackPermissionBoundaryTests.MvcTestConfiguration.class)
class FulfillmentCallbackPermissionBoundaryTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private LogisticsCallbackService callbacks;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TrustedUserContext.clear();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void clearTrustedUser() {
        TrustedUserContext.clear();
    }

    @Test
    void logisticsCallbackIsAcceptedWithoutEndUserPermissionContext() throws Exception {
        mockMvc.perform(post("/api/shipping/callbacks/logistics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timestampEpochSecond\":1}"))
                .andExpect(status().isOk());

        verify(callbacks).handle(any());
    }

    @Test
    void otherShippingEndpointsStillRequireTrustedUserContext() throws Exception {
        mockMvc.perform(get("/api/shipping/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(FulfillmentPermissionMvcConfiguration.class)
    static class MvcTestConfiguration {
        @Bean
        PermissionLookup permissionLookup() {
            return ignored -> Set.of(ServicePermissionCodes.SHIPPING_READ);
        }

        @Bean
        LogisticsCallbackService logisticsCallbackService() {
            return mock(LogisticsCallbackService.class);
        }

        @Bean
        LogisticsCallbackController logisticsCallbackController(LogisticsCallbackService service) {
            return new LogisticsCallbackController(service);
        }

        @Bean
        ProtectedShippingProbeController protectedShippingProbeController() {
            return new ProtectedShippingProbeController();
        }
    }

    @RestController
    static class ProtectedShippingProbeController {
        @GetMapping("/api/shipping/protected")
        @RequirePermission(ServicePermissionCodes.SHIPPING_READ)
        String protectedRead() {
            return "protected";
        }
    }
}
