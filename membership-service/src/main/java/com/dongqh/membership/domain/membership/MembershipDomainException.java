package com.dongqh.membership.domain.membership;

import com.dongqh.membership.enums.membership.MembershipErrorCode;

public class MembershipDomainException extends RuntimeException {
    private final MembershipErrorCode errorCode;

    public MembershipDomainException(MembershipErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public MembershipErrorCode errorCode() {
        return errorCode;
    }
}
