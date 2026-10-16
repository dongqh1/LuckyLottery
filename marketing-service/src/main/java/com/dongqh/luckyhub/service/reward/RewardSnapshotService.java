package com.dongqh.luckyhub.service.reward;

import com.dongqh.luckyhub.domain.po.prize.MarketingPrize;
import com.dongqh.luckyhub.domain.model.reward.RewardSnapshot;
import java.util.List;
import java.util.Map;

public interface RewardSnapshotService {
    Map<Long, RewardSnapshot> resolveForPrizes(List<MarketingPrize> prizes);
}
