package com.dongqh.pay.marketing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponReservationView {
    private Long userCouponId;
    private Long templateId;
    private String couponName;
    private Integer discountCent;
}
