package com.dongqh.lotteryservice.domain.model.draweligibility;

import com.dongqh.lotteryservice.domain.model.drawtransaction.DrawPrizeCandidate;

import java.time.LocalDateTime;
import java.util.List;

public record DrawEligibility(long activityId, int noWinWeight,
                                  List<DrawPrizeCandidate> prizes, LocalDateTime evaluatedAt) {
    public DrawEligibility {
        prizes = List.copyOf(prizes);
    }
}
