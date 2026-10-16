package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.domain.po.lottery.LotteryDrawOrder;
import com.dongqh.luckyhub.domain.model.lottery.NewDrawOrder;

import java.time.LocalDateTime;

public interface DrawOrderLifecycleService {

    LotteryDrawOrder createProcessing(NewDrawOrder command);

    void markFailed(long orderId, String safeReason);

    void markFailedAndRequestRelease(LotteryDrawOrder order, String safeReason, LocalDateTime occurredAt);
}
