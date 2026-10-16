package com.dongqh.luckyhub.domain.channel.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InitializeSkuStockCommand(
        @NotNull @Positive Long skuId,
        @NotNull @Positive Integer totalStock,
        @NotBlank @Size(max = 100) String businessNo
) {
}
