package com.dongqh.hmgateway;

import com.dongqh.hmgateway.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false"
})
class HmGatewayApplicationTests {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private AuthProperties authProperties;

    @Test
    void contextLoads() {
    }

    @Test
    void keepsTheFiveServiceRoutes() {
        Map<String, String> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .blockOptional()
                .orElseThrow()
                .stream()
                .collect(Collectors.toMap(RouteDefinition::getId, route -> route.getUri().toString()));

        assertEquals(Map.of(
                "item-service", "lb://item-service",
                "cart-service", "lb://cart-service",
                "user-service", "lb://user-service",
                "trade-service", "lb://trade-service",
                "pay-service", "lb://pay-service"
        ), routes);
    }

    @Test
    void keepsAnonymousPaths() {
        assertTrue(authProperties.getExcludePaths().containsAll(java.util.List.of(
                "GET /search/**",
                "POST /search/filters",
                "POST /users/login",
                "GET /items/**",
                "GET /hi"
        )));
    }

}
