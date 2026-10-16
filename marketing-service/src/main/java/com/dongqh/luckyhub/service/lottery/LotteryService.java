package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.domain.dto.lottery.DrawCommand;
import com.dongqh.luckyhub.domain.vo.lottery.DrawOrderView;

public interface LotteryService {
    DrawOrderView draw(DrawCommand command);
    DrawOrderView getByRequestId(String requestId);
}
