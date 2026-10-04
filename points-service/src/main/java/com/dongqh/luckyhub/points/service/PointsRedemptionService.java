package com.dongqh.luckyhub.points.service;

import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.points.dto.PointsRedemptionQuery;
import com.dongqh.luckyhub.points.dto.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.points.vo.PointsPageResponse;
import com.dongqh.luckyhub.points.vo.PointsRedemptionView;

public interface PointsRedemptionService {
    PointsRedemptionView create(long userId, String authorization, CreatePointsRedemptionCommand command);

    PointsRedemptionView get(long userId, String redemptionNo);

    PointsPageResponse<PointsRedemptionView> page(long userId, PointsRedemptionQuery query);

    PointsRedemptionView reverse(String redemptionNo, ReversePointsRedemptionCommand command);
}
