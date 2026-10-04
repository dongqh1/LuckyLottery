package com.dongqh.luckyhub.coupon.vo;

import java.util.List;

public record CouponPageResponse<T>(List<T> records, long total, long page, long size, long pages) {
    public CouponPageResponse {
        records = List.copyOf(records);
    }
}
