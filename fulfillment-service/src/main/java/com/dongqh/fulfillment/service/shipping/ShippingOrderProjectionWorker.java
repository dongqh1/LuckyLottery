package com.dongqh.fulfillment.service.shipping;

import com.dongqh.fulfillment.enums.shipping.ShippingStatus;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrder;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingOrderProjectionWorker {

    private final ShippingOrderRepository orders;
    private final ShippingOrderFulfillmentProjector projector;

    public ShippingOrderProjectionWorker(
            ShippingOrderRepository orders,
            ShippingOrderFulfillmentProjector projector
    ) {
        this.orders = orders;
        this.projector = projector;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean projectOne(long shippingOrderId) {
        ShippingOrder order = orders.lockById(shippingOrderId).orElse(null);
        if (order == null || order.status() == ShippingStatus.DELIVERED
                || order.status() == ShippingStatus.TERMINATED || order.fulfillmentNo() == null) {
            return false;
        }
        projector.projectFulfillmentState(order.fulfillmentNo());
        return true;
    }
}
