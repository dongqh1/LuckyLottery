package com.dongqh.luckyhub.messaging.coupon;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.luckyhub.config.coupon.PayCouponMessagingConfiguration;
import com.dongqh.luckyhub.service.coupon.CouponCheckoutService;
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
