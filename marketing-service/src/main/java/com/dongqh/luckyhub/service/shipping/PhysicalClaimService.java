package com.dongqh.luckyhub.service.shipping;

import com.dongqh.luckyhub.domain.dto.shipping.ClaimPhysicalBenefitCommand;
import com.dongqh.luckyhub.domain.vo.shipping.ShippingOrderView;

public interface PhysicalClaimService {
    ShippingOrderView claim(long userId, long benefitId, ClaimPhysicalBenefitCommand command);
}
