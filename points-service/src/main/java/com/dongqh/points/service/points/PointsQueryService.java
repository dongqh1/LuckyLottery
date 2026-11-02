package com.dongqh.points.service.points;

import com.dongqh.points.domain.dto.points.PointsLedgerQuery;
import com.dongqh.points.domain.vo.points.PointsAccountView;
import com.dongqh.points.domain.vo.points.PointsLedgerView;
import com.dongqh.points.domain.vo.points.PointsPageResponse;

public interface PointsQueryService {
    PointsAccountView getAccount(long userId);

    PointsPageResponse<PointsLedgerView> pageLedgers(long userId, PointsLedgerQuery query);
}
