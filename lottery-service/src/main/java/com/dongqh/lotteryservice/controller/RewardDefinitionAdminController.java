package com.dongqh.lotteryservice.controller;

import com.dongqh.lotteryservice.domain.dto.prizecatalog.CreateRewardDefinitionCommand;
import com.dongqh.lotteryservice.service.prizecatalog.RewardDefinitionAdminService;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.RewardDefinitionView;
import com.hmall.common.domain.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/reward-definitions")
public class RewardDefinitionAdminController {
    private final RewardDefinitionAdminService rewards;

    public RewardDefinitionAdminController(RewardDefinitionAdminService rewards) { this.rewards = rewards; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public R<RewardDefinitionView> create(@Valid @RequestBody CreateRewardDefinitionCommand command) {
        return R.ok(rewards.create(command));
    }

    @GetMapping("/{id}")
    public R<RewardDefinitionView> get(@PathVariable("id") @Positive long id) {
        return R.ok(rewards.get(id));
    }
}

