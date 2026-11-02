package com.dongqh.membership.web.membership;

import com.dongqh.membership.domain.membership.MembershipDomainException;
import com.hmall.common.domain.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.dongqh.membership")
public class MembershipExceptionHandler {
    @ExceptionHandler(MembershipDomainException.class)
    public ResponseEntity<R<Void>> handleMembershipError(MembershipDomainException exception) {
        var code = exception.errorCode();
        return ResponseEntity.status(code.httpStatus()).body(R.error(code.code(), code.message()));
    }
}
