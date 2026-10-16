package com.dongqh.luckyhub.enums.lottery;

import com.dongqh.luckyhub.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public enum LotteryErrorCode implements ErrorCode {

    ACTIVITY_NOT_FOUND(43001, "抽奖活动不存在", HttpStatus.NOT_FOUND),
    ACTIVITY_NOT_AVAILABLE(43002, "当前活动不可参与抽奖", HttpStatus.CONFLICT),
    DRAW_PARAMETER_INVALID(43003, "抽奖参数不合法", HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_CONFLICT(43004, "重复请求的参数不一致", HttpStatus.CONFLICT),
    DRAW_ORDER_PROCESSING(43005, "抽奖订单正在处理中", HttpStatus.CONFLICT),
    DRAW_ORDER_FAILED(43006, "抽奖订单处理失败", HttpStatus.CONFLICT),
    DRAW_ACCESS_DENIED(43007, "无权访问该抽奖数据", HttpStatus.FORBIDDEN),
    DRAW_LOCK_UNAVAILABLE(53001, "抽奖请求正在处理中", HttpStatus.CONFLICT),
    DRAW_WEIGHT_INVALID(53002, "抽奖配置没有有效权重", HttpStatus.INTERNAL_SERVER_ERROR),
    DRAW_TRANSACTION_FAILED(53003, "抽奖事务处理失败", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    LotteryErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}


