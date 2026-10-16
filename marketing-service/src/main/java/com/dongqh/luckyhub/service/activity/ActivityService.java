package com.dongqh.luckyhub.service.activity;

import com.dongqh.luckyhub.domain.dto.activity.ActivityQuery;
import com.dongqh.luckyhub.domain.dto.activity.CreateActivityCommand;
import com.dongqh.luckyhub.domain.dto.activity.UpdateActivityCommand;
import com.dongqh.luckyhub.domain.vo.activity.ActivityView;
import com.dongqh.luckyhub.common.result.PageResponse;

public interface ActivityService {

    ActivityView create(CreateActivityCommand command);

    ActivityView getById(long id);

    PageResponse<ActivityView> page(ActivityQuery query);

    ActivityView update(long id, UpdateActivityCommand command);

    ActivityView publish(long id);

    void disable(long id);

    ActivityView restore(long id);
}
