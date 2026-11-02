package com.dongqh.membership.enums.membership;

import org.springframework.http.HttpStatus;

public enum MembershipErrorCode {
    PRODUCT_NOT_FOUND(49001, "会员产品不存在", HttpStatus.NOT_FOUND),
    PRODUCT_DUPLICATE(49002, "会员产品编码重复", HttpStatus.CONFLICT),
    PRODUCT_INVALID(49005, "会员产品配置不合法", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    MembershipErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
