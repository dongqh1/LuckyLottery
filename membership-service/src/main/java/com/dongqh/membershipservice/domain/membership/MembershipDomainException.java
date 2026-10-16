package com.dongqh.membershipservice.domain.membership;

import com.dongqh.membershipservice.enums.membership.MembershipErrorCode;

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
