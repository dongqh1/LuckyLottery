package com.dongqh.hmapi.dto;

public record ReservePointsStockRequest(
        Long skuId,
        Integer quantity,
        String reservationNo
) {
}
