package com.dongqh.hmsecurity.permission;

import java.util.Set;

@FunctionalInterface
public interface PermissionCodeClient {
    Set<String> findPermissionCodes(Long userId, String authorization);
}
