package com.dongqh.luckyhub.service.points;

import com.dongqh.luckyhub.domain.dto.points.PointsLedgerQuery;
import com.dongqh.luckyhub.domain.vo.points.PointsAccountView;
import com.dongqh.luckyhub.domain.vo.points.PointsLedgerView;
import com.dongqh.luckyhub.domain.vo.points.PointsPageResponse;

public interface PointsQueryService {
    PointsAccountView getAccount(long userId);

    PointsPageResponse<PointsLedgerView> pageLedgers(long userId, PointsLedgerQuery query);
}
