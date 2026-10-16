package com.dongqh.lotteryservice.domain.model.drawchance;

import com.dongqh.lotteryservice.enums.drawchance.DrawChanceReservationStatus;

import java.time.LocalDate;

public record DrawChanceReservationResult(String requestId, long activityId, long userId,
        int drawCount, LocalDate drawDate, long bonusReserved, long cumulativeBonusForDate,
        DrawChanceReservationStatus status, boolean duplicate) {
}
