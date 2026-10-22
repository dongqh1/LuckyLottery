package com.dongqh.hmapi.dto;

import java.time.LocalDateTime;

/** Frozen shipping details read by the logistics service after payment. */
public record TradeOrderShippingDetailsDTO(Long orderId, Long userId, LocalDateTime paidAt,
                                           String contact, String mobile, String province,
                                           String city, String town, String street,
                                           String skuCode, String productName,
                                           String imageUrl, Integer quantity) {
}
