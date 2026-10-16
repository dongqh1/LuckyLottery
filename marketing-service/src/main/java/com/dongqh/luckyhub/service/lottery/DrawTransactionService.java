package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.domain.model.lottery.DrawExecutionContext;
import com.dongqh.luckyhub.domain.model.lottery.DrawExecutionResult;

public interface DrawTransactionService {

    DrawExecutionResult execute(DrawExecutionContext context);
}
