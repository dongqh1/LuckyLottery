package com.dongqh.points.service.points;

import com.dongqh.points.domain.dto.points.AdminPointsAdjustmentCommand;
import com.dongqh.points.domain.dto.points.PointsMutationCommand;
import com.dongqh.points.domain.dto.points.PointsReversalCommand;
import com.dongqh.points.domain.vo.points.PointsAccountView;
import com.dongqh.points.domain.vo.points.PointsLedgerView;

public interface PointsAccountService {
    PointsLedgerView credit(PointsMutationCommand command);

    PointsLedgerView debit(PointsMutationCommand command);

    PointsLedgerView reverseDebit(PointsReversalCommand command);

    PointsLedgerView adjust(AdminPointsAdjustmentCommand command);

    PointsAccountView get(long userId);
}
