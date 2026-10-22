package com.dongqh.lotteryservice.enums.prizecatalog;

import org.springframework.http.HttpStatus;

public enum RewardDefinitionErrorCode {
    REWARD_NOT_FOUND(45001, "奖励定义不存在", HttpStatus.NOT_FOUND),
    REWARD_CODE_DUPLICATE(45002, "奖励编码已存在", HttpStatus.CONFLICT),
    REWARD_TARGET_INVALID(45003, "奖励目标不合法", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    RewardDefinitionErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() { return code; }
    public String message() { return message; }
    public HttpStatus httpStatus() { return httpStatus; }
}
