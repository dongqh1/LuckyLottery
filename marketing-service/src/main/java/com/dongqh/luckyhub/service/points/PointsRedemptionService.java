package com.dongqh.luckyhub.service.points;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.domain.dto.points.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.domain.dto.points.PointsRedemptionQuery;
import com.dongqh.luckyhub.domain.dto.points.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.domain.vo.points.PointsRedemptionView;

public interface PointsRedemptionService {
    PointsRedemptionView create(long userId, CreatePointsRedemptionCommand command);

    PointsRedemptionView get(long userId, String redemptionNo);

    PageResponse<PointsRedemptionView> page(long userId, PointsRedemptionQuery query);

    PointsRedemptionView reverse(String redemptionNo, ReversePointsRedemptionCommand command);
}
