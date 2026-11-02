package com.dongqh.points.web;

import com.dongqh.points.domain.exception.PointsDomainException;
import com.hmall.common.domain.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.dongqh.points")
public class PointsExceptionHandler {

    @ExceptionHandler(PointsDomainException.class)
    public ResponseEntity<R<Void>> handlePointsError(PointsDomainException exception) {
        var code = exception.errorCode();
        return ResponseEntity.status(code.httpStatus())
                .body(R.error(code.code(), code.message()));
    }
}
