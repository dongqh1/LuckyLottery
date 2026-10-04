package com.dongqh.hmgateway;

import com.dongqh.hmgateway.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.List;
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
    void routesMarketingDomainsToExtractedServices() {
        List<RouteDefinition> routeDefinitions = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .blockOptional()
                .orElseThrow();
        Map<String, String> routes = routeDefinitions.stream()
                .collect(Collectors.toMap(RouteDefinition::getId, route -> route.getUri().toString()));

        assertEquals(Map.ofEntries(
                Map.entry("item-service", "lb://item-service"),
                Map.entry("cart-service", "lb://cart-service"),
                Map.entry("user-service", "lb://user-service"),
                Map.entry("trade-service", "lb://trade-service"),
                Map.entry("pay-service", "lb://pay-service"),
                Map.entry("points-service", "lb://points-service"),
                Map.entry("coupon-service", "lb://coupon-service"),
                Map.entry("membership-service", "lb://membership-service"),
                Map.entry("lottery-service", "lb://lottery-service"),
                Map.entry("fulfillment-service", "lb://fulfillment-service")
        ), routes);
        assertEquals(List.of(
                "item-service",
                "cart-service",
                "user-service",
                "trade-service",
                "pay-service",
                "points-service",
                "coupon-service",
                "membership-service",
                "lottery-service",
                "fulfillment-service"
        ), routeDefinitions.stream().map(RouteDefinition::getId).toList());
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
