package com.dongqh.hmsecurity.permission;

import java.util.Set;

/**
 * Resolves effective permission codes for one trusted user ID.
 * Implementations must not synthesize permissions when the authority is unavailable.
 */
@FunctionalInterface
public interface PermissionLookup {
    Set<String> findPermissionCodes(Long userId);
}
