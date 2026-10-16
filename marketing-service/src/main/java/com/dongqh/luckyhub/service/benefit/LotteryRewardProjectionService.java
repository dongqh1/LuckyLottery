package com.dongqh.luckyhub.service.benefit;

public interface LotteryRewardProjectionService {
    int projectBatch(int limit);

    void project(long benefitId);
}
