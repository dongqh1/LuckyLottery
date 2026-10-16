package com.dongqh.luckyhub.service.activity;

import com.dongqh.luckyhub.domain.dto.activity.AddActivityPrizeCommand;
import com.dongqh.luckyhub.domain.dto.activity.UpdateActivityPrizeCommand;
import com.dongqh.luckyhub.domain.vo.activity.ActivityPrizeView;

import java.util.List;

public interface ActivityPrizeService {

    ActivityPrizeView add(long activityId, AddActivityPrizeCommand command);

    List<ActivityPrizeView> list(long activityId);

    ActivityPrizeView update(
            long activityId,
            long prizeId,
            UpdateActivityPrizeCommand command
    );

    void remove(long activityId, long prizeId);
}
