package com.dongqh.luckyhub.domain.dto.payment;

import com.dongqh.luckyhub.enums.payment.PaymentResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SimulatePaymentCommand(@NotNull PaymentResult result, @Size(max = 255) String failureReason) {
}
