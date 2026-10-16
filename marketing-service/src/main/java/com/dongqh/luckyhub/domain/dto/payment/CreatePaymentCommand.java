package com.dongqh.luckyhub.domain.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePaymentCommand(
        @NotBlank @Size(max = 64) String paymentNo,
        @NotBlank @Size(max = 64) String orderNo
) {
}
