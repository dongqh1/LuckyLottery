package com.dongqh.hmsecurity.context;

public class MissingTrustedUserException extends RuntimeException {
    public MissingTrustedUserException() {
        super("Trusted user context is missing");
    }
}

