package com.dongqh.coupon.messaging;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.coupon.service.coupon.CouponCheckoutService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true")
public class PaySuccessCouponListener {

    private final CouponCheckoutService coupons;

    public PaySuccessCouponListener(CouponCheckoutService coupons) {
        this.coupons = coupons;
    }

    @RabbitListener(queues = CouponMessagingTopology.QUEUE)
    public void onPaySuccess(PaySuccessMessage event) {
        if (event.couponId() == null) {
            return;
        }
        coupons.use(event.userId(), event.couponId(), event.orderId().toString());
    }
}
