package com.dongqh.luckyhub.service.shipping;

import com.dongqh.luckyhub.domain.vo.shipping.ShippingTrackingView;

public interface ShippingQueryService {
    ShippingTrackingView getForUser(long userId, String shippingNo);
}
