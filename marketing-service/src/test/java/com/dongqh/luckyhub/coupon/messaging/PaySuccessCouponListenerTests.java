package com.dongqh.luckyhub.coupon.messaging;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.luckyhub.coupon.service.CouponCheckoutService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

class PaySuccessCouponListenerTests {
    private final CouponCheckoutService coupons = mock(CouponCheckoutService.class);
    private final PaySuccessCouponListener listener = new PaySuccessCouponListener(coupons);

    @Test
    void consumesTheCouponLockedByThePaidOrder() {
        listener.onPaySuccess(event(31L));

        verify(coupons).use(7L, 31L, "101");
    }

    @Test
    void skipsOrdersThatDidNotUseACoupon() {
        listener.onPaySuccess(event(null));

        verifyNoInteractions(coupons);
    }

    @Test
    void propagatesCouponFailureSoRabbitCanRetryOrDeadLetter() {
        doThrow(new IllegalStateException("coupon conflict"))
                .when(coupons).use(7L, 31L, "101");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.onPaySuccess(event(31L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("coupon conflict");
    }

    private PaySuccessMessage event(Long couponId) {
        return new PaySuccessMessage("evt-101", 101L, 7L, 8500, couponId,
                LocalDateTime.of(2026, 9, 17, 12, 30));
    }
}
