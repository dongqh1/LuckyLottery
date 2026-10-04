package com.dongqh.lotteryservice.quota;

import java.time.LocalDate;

public record QuotaReservationResult(
        String requestId,
        ReservationStatus status,
        LocalDate drawDate,
        int drawCount,
        boolean duplicate
) {
}
