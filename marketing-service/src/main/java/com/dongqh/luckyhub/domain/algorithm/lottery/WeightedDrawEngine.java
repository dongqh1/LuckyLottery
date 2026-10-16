package com.dongqh.luckyhub.domain.algorithm.lottery;

import java.util.List;

public interface WeightedDrawEngine {

    DrawCandidate select(List<PrizeWeightSnapshot> prizes, int noWinWeight);
}
