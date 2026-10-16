package com.dongqh.luckyhub.domain.vo.payment;

import com.dongqh.luckyhub.enums.payment.PaymentResult;
import com.dongqh.luckyhub.enums.payment.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentView(
        Long id,
        String paymentNo,
        String orderNo,
        Long userId,
        Long amountCent,
        PaymentStatus status,
        PaymentResult callbackResult,
        String failureReason,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
