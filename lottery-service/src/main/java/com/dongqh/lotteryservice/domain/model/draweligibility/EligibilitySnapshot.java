package com.dongqh.lotteryservice.domain.model.draweligibility;

import com.dongqh.lotteryservice.domain.model.drawtransaction.DrawPrizeSnapshot;

import java.time.LocalDateTime;
import java.util.List;

public record EligibilitySnapshot(long activityId, int noWinWeight,
                                  List<DrawPrizeSnapshot> prizes, LocalDateTime snapshotTime) {
    public EligibilitySnapshot {
        prizes = List.copyOf(prizes);
    }
}
