package com.dongqh.membershipservice.membership.domain;

import com.dongqh.membershipservice.membership.enums.MembershipErrorCode;

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
