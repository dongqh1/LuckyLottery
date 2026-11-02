package com.dongqh.pay.marketing;


import com.dongqh.hmapi.dto.OrderDetailDTO;

import java.util.List;

public interface CouponCheckoutClient {
    CouponReservationView reserve(Long couponId, Long orderId, List<OrderDetailDTO> details);

    void use(Long couponId, Long orderId);

    void release(Long couponId, Long orderId);
}
