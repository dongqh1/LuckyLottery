package com.dongqh.coupon.messaging;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.coupon.service.coupon.CouponCheckoutService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

        assertThatThrownBy(() -> listener.onPaySuccess(event(31L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("coupon conflict");
    }

    private PaySuccessMessage event(Long couponId) {
        return new PaySuccessMessage("evt-101", 101L, 7L, 8500, couponId,
                LocalDateTime.of(2026, 9, 17, 12, 30));
    }
}
