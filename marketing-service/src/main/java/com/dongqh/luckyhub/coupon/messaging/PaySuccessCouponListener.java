package com.dongqh.luckyhub.coupon.messaging;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.luckyhub.coupon.config.PayCouponMessagingConfiguration;
import com.dongqh.luckyhub.coupon.service.CouponCheckoutService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaySuccessCouponListener {
    private final CouponCheckoutService coupons;

    public PaySuccessCouponListener(CouponCheckoutService coupons) {
        this.coupons = coupons;
    }

    @RabbitListener(queues = PayCouponMessagingConfiguration.QUEUE)
    public void onPaySuccess(PaySuccessMessage event) {
        if (event.couponId() == null) {
            return;
        }
        coupons.use(event.userId(), event.couponId(), event.orderId().toString());
    }
}
