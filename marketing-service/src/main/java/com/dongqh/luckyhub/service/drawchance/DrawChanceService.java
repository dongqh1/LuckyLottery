package com.dongqh.luckyhub.service.drawchance;

import com.dongqh.luckyhub.domain.dto.drawchance.DrawChanceReservationCommand;
import com.dongqh.luckyhub.domain.model.drawchance.DrawChanceReservationResult;
import com.dongqh.luckyhub.domain.vo.drawchance.DrawChanceAccountView;
import java.time.LocalDateTime;

public interface DrawChanceService {
    DrawChanceAccountView credit(long userId, String businessId, long chances);
    DrawChanceReservationResult reserve(DrawChanceReservationCommand command);
    void confirm(String requestId);
    void release(String requestId);
    int reconcileExpired(int limit, LocalDateTime cutoff);
    DrawChanceAccountView get(long userId);
}
