package com.dongqh.luckyhub.service.reward;

import com.dongqh.luckyhub.domain.dto.reward.CreateRewardDefinitionCommand;
import com.dongqh.luckyhub.domain.vo.reward.RewardDefinitionView;

public interface RewardDefinitionService {
    RewardDefinitionView create(CreateRewardDefinitionCommand command);

    RewardDefinitionView get(long id);
}
