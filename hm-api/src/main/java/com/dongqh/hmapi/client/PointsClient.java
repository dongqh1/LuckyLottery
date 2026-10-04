package com.dongqh.hmapi.client;

import com.dongqh.hmapi.dto.PointsRewardCreditRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("points-service")
public interface PointsClient {
    @PostMapping("/internal/points/rewards/credit")
    void creditReward(@RequestBody PointsRewardCreditRequest request);
}
