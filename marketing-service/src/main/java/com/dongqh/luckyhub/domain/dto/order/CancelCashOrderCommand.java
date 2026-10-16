package com.dongqh.luckyhub.domain.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelCashOrderCommand(@NotBlank @Size(max = 500) String reason) {
}
