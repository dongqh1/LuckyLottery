package com.dongqh.luckyhub.rbac.controller;

import com.dongqh.hmsecurity.permission.PermissionLookup;
import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@RestController
@RequestMapping("/internal/permissions/users")
public class InternalPermissionController {
    private final PermissionLookup permissions;

    public InternalPermissionController(PermissionLookup permissions) {
        this.permissions = permissions;
    }

    @GetMapping("/{userId}")
    public Set<String> getMine(@PathVariable("userId") long userId) {
        LoginPrincipal principal = LoginContext.require();
        if (principal.userId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Set<String> resolved = permissions.findPermissionCodes(userId);
        return resolved == null ? Set.of() : Set.copyOf(resolved);
    }
}
