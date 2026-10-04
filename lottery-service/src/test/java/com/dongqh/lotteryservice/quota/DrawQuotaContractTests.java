package com.dongqh.lotteryservice.quota;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DrawQuotaContractTests {

    @Test
    void preservesRedisKeyFormatsAndStableReservationStatuses() {
        LocalDate drawDate = LocalDate.of(2026, 9, 25);

        assertThat(DrawQuotaKeys.quota(42, 101, drawDate))
                .isEqualTo("draw:quota:42:101:20260925");
        assertThat(DrawQuotaKeys.reservation("request-1"))
                .isEqualTo("draw:reservation:request-1");
        assertThat(DrawQuotaKeys.reservationTimeouts())
                .isEqualTo("draw:reservation:timeouts");
        assertThat(DrawQuotaKeys.drawLock(42, 101))
                .isEqualTo("lock:draw:42:101");
        assertThat(ReservationStatus.values())
                .containsExactly(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RELEASED);
    }

    @Test
    void keepsPolicyLimitOutsideTheReservationIdentityShape() {
        QuotaReservationRequest request = new QuotaReservationRequest("request-1", 42, 101, 10, 20);

        assertThat(request.drawDate()).isNull();
        assertThat(request.requestId()).isEqualTo("request-1");
        assertThat(request.activityId()).isEqualTo(42);
        assertThat(request.userId()).isEqualTo(101);
        assertThat(request.drawCount()).isEqualTo(10);
        assertThat(request.dailyLimit()).isEqualTo(20);
    }
}
