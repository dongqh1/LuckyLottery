package com.dongqh.pointsservice.controller;

import com.dongqh.hmapi.dto.PointsRewardCreditRequest;
import com.dongqh.luckyhub.points.dto.PointsMutationCommand;
import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/points/rewards")
public class InternalPointsRewardController {
    private final PointsAccountService accounts;

    public InternalPointsRewardController(PointsAccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/credit")
    public void credit(@RequestBody PointsRewardCreditRequest request) {
        accounts.credit(new PointsMutationCommand(request.targetUserId(), PointsBusinessType.LOTTERY_REWARD,
                request.fulfillmentNo(), request.points(), request.reason()));
    }
}
