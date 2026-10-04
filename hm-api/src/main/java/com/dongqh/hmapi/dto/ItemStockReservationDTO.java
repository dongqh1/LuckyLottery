package com.dongqh.hmapi.dto;

public record ItemStockReservationDTO(
        Long skuId,
        String channelCode,
        Integer quantity,
        String reservationNo,
        String status
) {
}
