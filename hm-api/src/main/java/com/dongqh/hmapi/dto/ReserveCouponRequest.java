package com.dongqh.hmapi.dto;

import java.util.List;

public record ReserveCouponRequest(long couponId, String orderNo, List<CouponCheckoutItemDTO> items) {
}
