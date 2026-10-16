package com.dongqh.luckyhub.service.points;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.domain.dto.points.PointsLedgerQuery;
import com.dongqh.luckyhub.domain.vo.points.PointsAccountView;
import com.dongqh.luckyhub.domain.vo.points.PointsLedgerView;

public interface PointsQueryService {
    PointsAccountView getAccount(long userId);

    PageResponse<PointsLedgerView> pageLedgers(long userId, PointsLedgerQuery query);
}
