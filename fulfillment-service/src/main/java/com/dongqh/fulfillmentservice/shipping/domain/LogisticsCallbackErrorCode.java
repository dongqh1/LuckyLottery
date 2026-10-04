package com.dongqh.fulfillmentservice.shipping.domain;

public enum LogisticsCallbackErrorCode {
    SHIPPING_NOT_FOUND(55005, "发货单不存在"),
    SHIPPING_STATE_CONFLICT(55007, "发货单状态冲突"),
    CALLBACK_SIGNATURE_INVALID(55010, "物流回调验签失败"),
    CALLBACK_REPLAYED(55011, "物流回调已处理"),
    SHIPPING_REQUEST_INVALID(55012, "物流请求参数不合法");

    private final int code;
    private final String message;

    LogisticsCallbackErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public static LogisticsCallbackErrorCode fromCode(String code) {
        for (LogisticsCallbackErrorCode candidate : values()) {
            if (Integer.toString(candidate.code).equals(code)) {
                return candidate;
            }
        }
        return CALLBACK_REPLAYED;
    }
}
