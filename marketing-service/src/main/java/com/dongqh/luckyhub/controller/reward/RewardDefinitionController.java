package com.dongqh.luckyhub.controller.reward;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.domain.dto.reward.CreateRewardDefinitionCommand;
import com.dongqh.luckyhub.service.reward.RewardDefinitionService;
import com.dongqh.luckyhub.domain.vo.reward.RewardDefinitionView;
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
public class RewardDefinitionController {

    private final RewardDefinitionService service;

    public RewardDefinitionController(RewardDefinitionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RewardDefinitionView> create(
            @Valid @RequestBody CreateRewardDefinitionCommand command
    ) {
        return ApiResponse.success(service.create(command));
    }

    @GetMapping("/{id}")
    public ApiResponse<RewardDefinitionView> get(@PathVariable @Positive long id) {
        return ApiResponse.success(service.get(id));
    }
}

