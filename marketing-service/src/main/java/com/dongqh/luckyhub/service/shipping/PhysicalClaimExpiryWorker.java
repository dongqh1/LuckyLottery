package com.dongqh.luckyhub.service.shipping;

import java.time.LocalDateTime;

public interface PhysicalClaimExpiryWorker {
    boolean expireOne(long benefitId, LocalDateTime now);
}
