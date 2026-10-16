package com.dongqh.luckyhub.domain.channel.inventory.vo;

import com.dongqh.luckyhub.domain.channel.inventory.enums.InventoryReservationStatus;

public record ChannelInventoryView(
        Long skuId,
        String channelCode,
        Integer totalStock,
        Integer allocatedStock,
        Integer availableStock,
        Integer reservedStock,
        Integer consumedStock,
        String reservationNo,
        Integer reservationQuantity,
        InventoryReservationStatus reservationStatus
) {
}
