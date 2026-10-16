package com.dongqh.luckyhub.service.points;

import com.dongqh.luckyhub.domain.dto.points.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.domain.dto.points.PointsRedemptionQuery;
import com.dongqh.luckyhub.domain.dto.points.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.domain.vo.points.PointsPageResponse;
import com.dongqh.luckyhub.domain.vo.points.PointsRedemptionView;

public interface PointsRedemptionService {
    PointsRedemptionView create(long userId, String authorization, CreatePointsRedemptionCommand command);

    PointsRedemptionView get(long userId, String redemptionNo);

    PointsPageResponse<PointsRedemptionView> page(long userId, PointsRedemptionQuery query);

    PointsRedemptionView reverse(String redemptionNo, ReversePointsRedemptionCommand command);
}
