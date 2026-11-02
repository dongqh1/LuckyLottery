package com.dongqh.lottery.domain.model.draweligibility;

import com.dongqh.lottery.domain.model.drawtransaction.DrawPrizeCandidate;

import java.time.LocalDateTime;
import java.util.List;

public record DrawEligibility(long activityId, int noWinWeight,
                                  List<DrawPrizeCandidate> prizes, LocalDateTime evaluatedAt) {
    public DrawEligibility {
        prizes = List.copyOf(prizes);
    }
}
