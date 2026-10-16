package com.dongqh.luckyhub.domain.model.drawchance;

import com.dongqh.luckyhub.enums.drawchance.DrawChanceReservationStatus;
import java.time.LocalDate;

public record DrawChanceReservationResult(String requestId, long activityId, long userId,
        int drawCount, LocalDate drawDate, long bonusReserved, long cumulativeBonusForDate,
        DrawChanceReservationStatus status, boolean duplicate) {
}
