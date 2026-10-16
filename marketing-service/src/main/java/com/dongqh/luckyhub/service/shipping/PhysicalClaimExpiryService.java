package com.dongqh.luckyhub.service.shipping;

import java.time.LocalDateTime;

public interface PhysicalClaimExpiryService {
    int expireDue(int limit, LocalDateTime now);
}
