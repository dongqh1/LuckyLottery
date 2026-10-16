package com.dongqh.luckyhub.service.points;

import com.dongqh.luckyhub.domain.dto.points.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.domain.dto.points.PointsMutationCommand;
import com.dongqh.luckyhub.domain.dto.points.PointsReversalCommand;
import com.dongqh.luckyhub.domain.vo.points.PointsAccountView;
import com.dongqh.luckyhub.domain.vo.points.PointsLedgerView;

public interface PointsAccountService {
    PointsLedgerView credit(PointsMutationCommand command);

    PointsLedgerView debit(PointsMutationCommand command);

    PointsLedgerView reverseDebit(PointsReversalCommand command);

    PointsLedgerView adjust(AdminPointsAdjustmentCommand command);

    PointsAccountView get(long userId);
}
