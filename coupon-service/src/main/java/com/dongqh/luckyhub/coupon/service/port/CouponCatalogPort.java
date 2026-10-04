package com.dongqh.luckyhub.coupon.service.port;

/** Read-only catalog boundary. Implementations must never return a synthetic item on lookup failure. */
public interface CouponCatalogPort {
    CouponItemSnapshot getItem(long itemId);
}
