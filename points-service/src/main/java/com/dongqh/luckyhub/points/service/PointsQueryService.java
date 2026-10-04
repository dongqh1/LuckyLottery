package com.dongqh.luckyhub.points.service;

import com.dongqh.luckyhub.points.dto.PointsLedgerQuery;
import com.dongqh.luckyhub.points.vo.PointsAccountView;
import com.dongqh.luckyhub.points.vo.PointsLedgerView;
import com.dongqh.luckyhub.points.vo.PointsPageResponse;

public interface PointsQueryService {
    PointsAccountView getAccount(long userId);

    PointsPageResponse<PointsLedgerView> pageLedgers(long userId, PointsLedgerQuery query);
}
