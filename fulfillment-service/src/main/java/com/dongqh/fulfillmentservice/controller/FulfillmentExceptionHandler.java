package com.dongqh.fulfillmentservice.controller;

import com.dongqh.fulfillmentservice.service.FulfillmentTaskIdempotencyConflictException;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskNotFoundException;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskOperationConflictException;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskOperationNotAllowedException;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskInvalidPayloadException;
import com.dongqh.fulfillmentservice.service.FulfillmentLeaseConflictException;
import com.dongqh.fulfillmentservice.domain.shipping.LogisticsCallbackErrorCode;
import com.dongqh.fulfillmentservice.domain.shipping.LogisticsCallbackException;
import com.hmall.common.domain.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.dongqh.fulfillmentservice")
public class FulfillmentExceptionHandler {
    @ExceptionHandler(FulfillmentTaskNotFoundException.class)
    public ResponseEntity<R<Void>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(R.error(52001, "履约任务不存在"));
    }

    @ExceptionHandler(FulfillmentTaskIdempotencyConflictException.class)
    public ResponseEntity<R<Void>> idempotencyConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(R.error(52002, "履约幂等参数冲突"));
    }

    @ExceptionHandler({FulfillmentTaskOperationConflictException.class, FulfillmentLeaseConflictException.class})
    public ResponseEntity<R<Void>> stateConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(R.error(52004, "履约任务状态冲突"));
    }

    @ExceptionHandler(FulfillmentTaskOperationNotAllowedException.class)
    public ResponseEntity<R<Void>> operationNotAllowed() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(R.error(52010, "当前状态不允许该操作"));
    }

    @ExceptionHandler({FulfillmentTaskInvalidPayloadException.class, IllegalArgumentException.class})
    public ResponseEntity<R<Void>> invalidRequest() {
        return ResponseEntity.badRequest().body(R.error(52003, "履约参数不合法"));
    }

    @ExceptionHandler(LogisticsCallbackException.class)
    public ResponseEntity<R<Void>> logisticsCallbackError(LogisticsCallbackException exception) {
        LogisticsCallbackErrorCode code = exception.errorCode();
        HttpStatus status = switch (code) {
            case SHIPPING_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SHIPPING_STATE_CONFLICT, CALLBACK_REPLAYED -> HttpStatus.CONFLICT;
            case CALLBACK_SIGNATURE_INVALID -> HttpStatus.UNAUTHORIZED;
            case SHIPPING_REQUEST_INVALID -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(R.error(code.code(), code.message()));
    }
}
