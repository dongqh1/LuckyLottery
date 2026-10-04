package com.dongqh.hmsecurity.permission;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class MissingTrustedUserException extends RuntimeException {
    public MissingTrustedUserException() {
        super("Trusted user context is missing");
    }
}
