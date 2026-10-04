package com.dongqh.hmsecurity.permission;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class PermissionLookupUnavailableException extends RuntimeException {
    public PermissionLookupUnavailableException(Throwable cause) {
        super("Permission authority is unavailable", cause);
    }
}
