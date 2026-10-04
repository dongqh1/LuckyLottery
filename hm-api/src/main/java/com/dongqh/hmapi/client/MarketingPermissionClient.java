package com.dongqh.hmapi.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Set;

@FeignClient("marketing-service")
public interface MarketingPermissionClient {
    @GetMapping("/internal/permissions/users/{userId}")
    Set<String> getPermissionCodes(
            @PathVariable("userId") Long userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );
}
