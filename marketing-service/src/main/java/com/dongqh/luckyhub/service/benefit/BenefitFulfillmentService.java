package com.dongqh.luckyhub.service.benefit;

public interface BenefitFulfillmentService {
    void fulfill(long benefitId, String eventId);
}
