package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.domain.model.lottery.DrawPrizeSnapshot;

import java.time.LocalDateTime;
import java.util.List;

public interface DrawEligibilityService {
    EligibilitySnapshot load(long activityId);

    record EligibilitySnapshot(long activityId, int noWinWeight,
                               List<DrawPrizeSnapshot> prizes, LocalDateTime snapshotTime) {
        public EligibilitySnapshot {
            prizes = List.copyOf(prizes);
        }
    }
}
