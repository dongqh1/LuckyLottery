package com.dongqh.luckyhub.service.shipping;

import com.dongqh.luckyhub.domain.model.shipping.CreateShippingOrderCommand;
import com.dongqh.luckyhub.domain.vo.shipping.ShippingOrderView;

public interface ShippingOrderService {
    ShippingOrderView create(CreateShippingOrderCommand command);
    ShippingOrderView getForUser(long userId, String shippingNo);
    void projectFulfillmentState(String fulfillmentNo);
}
