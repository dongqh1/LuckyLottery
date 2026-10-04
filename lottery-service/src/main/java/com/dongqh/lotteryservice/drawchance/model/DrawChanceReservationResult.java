package com.dongqh.lotteryservice.drawchance.model;

import com.dongqh.lotteryservice.drawchance.enums.DrawChanceReservationStatus;

import java.time.LocalDate;

public record DrawChanceReservationResult(String requestId, long activityId, long userId,
        int drawCount, LocalDate drawDate, long bonusReserved, long cumulativeBonusForDate,
        DrawChanceReservationStatus status, boolean duplicate) {
}
