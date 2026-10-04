package com.dongqh.tradeservice.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateMembershipOrderRequest(
        @NotNull @Positive Long membershipProductId,
        @NotBlank @Size(max = 64) String requestKey
) {
}
