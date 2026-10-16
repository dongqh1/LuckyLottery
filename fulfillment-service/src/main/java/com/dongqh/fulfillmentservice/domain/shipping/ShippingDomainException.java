package com.dongqh.fulfillmentservice.domain.shipping;

public final class ShippingDomainException extends RuntimeException {

    private final ShippingErrorCode errorCode;

    public ShippingDomainException(ShippingErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public ShippingDomainException(ShippingErrorCode errorCode, Throwable cause) {
        super(errorCode.name(), cause);
        this.errorCode = errorCode;
    }

    public ShippingErrorCode errorCode() {
        return errorCode;
    }

    public ShippingErrorCode getErrorCode() {
        return errorCode;
    }
}
