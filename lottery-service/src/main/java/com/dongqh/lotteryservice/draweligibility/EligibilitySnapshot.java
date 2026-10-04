package com.dongqh.lotteryservice.draweligibility;

import com.dongqh.lotteryservice.drawtransaction.model.DrawPrizeSnapshot;

import java.time.LocalDateTime;
import java.util.List;

public record EligibilitySnapshot(long activityId, int dailyLimit, int noWinWeight,
                                  List<DrawPrizeSnapshot> prizes, LocalDateTime snapshotTime) {
    public EligibilitySnapshot {
        prizes = List.copyOf(prizes);
    }
}
