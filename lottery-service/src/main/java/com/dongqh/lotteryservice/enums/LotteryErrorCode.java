package com.dongqh.lotteryservice.enums;

import org.springframework.http.HttpStatus;

public enum LotteryErrorCode {
    ACTIVITY_NOT_FOUND(43001, "抽奖活动不存在", HttpStatus.NOT_FOUND),
    ACTIVITY_NOT_AVAILABLE(43002, "当前活动不可参与抽奖", HttpStatus.CONFLICT),
    DRAW_PARAMETER_INVALID(43003, "抽奖参数不合法", HttpStatus.BAD_REQUEST),
    DAILY_QUOTA_EXCEEDED(43004, "每日抽奖额度不足", HttpStatus.CONFLICT),
    TEN_DRAW_QUOTA_EXCEEDED(43005, "十连抽额度不足", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(43006, "重复请求的参数不一致", HttpStatus.CONFLICT),
    DRAW_ORDER_PROCESSING(43007, "抽奖订单正在处理中", HttpStatus.CONFLICT),
    DRAW_ORDER_FAILED(43008, "抽奖订单处理失败", HttpStatus.CONFLICT),
    DRAW_ACCESS_DENIED(43009, "无权访问该抽奖数据", HttpStatus.FORBIDDEN),
    DRAW_QUOTA_UNAVAILABLE(53001, "抽奖额度服务不可用", HttpStatus.SERVICE_UNAVAILABLE),
    DRAW_LOCK_UNAVAILABLE(53002, "抽奖请求正在处理中", HttpStatus.CONFLICT),
    DRAW_WEIGHT_INVALID(53003, "抽奖配置没有有效权重", HttpStatus.INTERNAL_SERVER_ERROR),
    DRAW_TRANSACTION_FAILED(53004, "抽奖事务处理失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DRAW_CHANCE_INVALID_REQUEST(46001, "抽奖次数请求不合法", HttpStatus.BAD_REQUEST),
    DRAW_CHANCE_USER_UNAVAILABLE(46002, "抽奖次数用户不可用", HttpStatus.CONFLICT),
    DRAW_CHANCE_IDENTITY_CONFLICT(46003, "抽奖次数请求身份冲突", HttpStatus.CONFLICT),
    DRAW_CHANCE_RESERVATION_NOT_FOUND(46004, "抽奖次数预留不存在", HttpStatus.NOT_FOUND),
    DRAW_CHANCE_TERMINAL_STATE_CONFLICT(46005, "抽奖次数预留终态冲突", HttpStatus.CONFLICT),
    DRAW_CHANCE_BALANCE_INVALID(46006, "抽奖次数账户余额异常", HttpStatus.CONFLICT),
    BENEFIT_NOT_FOUND(43010, "用户权益不存在", HttpStatus.NOT_FOUND),
    BENEFIT_ACCESS_DENIED(43011, "无权访问该用户权益", HttpStatus.FORBIDDEN),
    BENEFIT_QUERY_INVALID(43012, "权益查询参数不合法", HttpStatus.BAD_REQUEST),
    BENEFIT_CLAIM_NOT_ALLOWED(43013, "当前权益不可领取", HttpStatus.CONFLICT),
    BENEFIT_CLAIM_EXPIRED(43014, "实物权益领取期限已过", HttpStatus.CONFLICT),
    BENEFIT_CLAIM_IDENTITY_CONFLICT(43015, "实物履约单与权益身份不一致", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    LotteryErrorCode(int code, String message, HttpStatus httpStatus) {
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
