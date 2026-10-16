package com.dongqh.luckyhub.service.shipping;

import com.dongqh.luckyhub.domain.dto.shipping.LogisticsCallbackCommand;

public interface LogisticsCallbackService {
    void handle(LogisticsCallbackCommand command);
}
