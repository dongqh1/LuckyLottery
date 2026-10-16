package com.dongqh.luckyhub.service.shipping.impl;

import com.dongqh.luckyhub.domain.po.shipping.ShippingOrder;
import com.dongqh.luckyhub.enums.shipping.ShippingStatus;
import com.dongqh.luckyhub.mapper.shipping.ShippingOrderMapper;
import com.dongqh.luckyhub.service.shipping.ShippingOrderService;
import com.dongqh.luckyhub.service.shipping.ShippingProjectionWorker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingProjectionWorkerImpl implements ShippingProjectionWorker {
    private final ShippingOrderMapper orders;
    private final ShippingOrderService shippingOrders;

    public ShippingProjectionWorkerImpl(ShippingOrderMapper orders, ShippingOrderService shippingOrders) {
        this.orders = orders;
        this.shippingOrders = shippingOrders;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean projectOne(long shippingOrderId) {
        ShippingOrder order = orders.lockById(shippingOrderId);
        if (order == null || order.getStatus() == ShippingStatus.DELIVERED
                || order.getStatus() == ShippingStatus.TERMINATED || order.getFulfillmentNo() == null) {
            return false;
        }
        shippingOrders.projectFulfillmentState(order.getFulfillmentNo());
        return true;
    }
}
