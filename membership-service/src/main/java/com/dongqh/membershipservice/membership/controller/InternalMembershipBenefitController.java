package com.dongqh.membershipservice.membership.controller;

import com.dongqh.hmapi.dto.MembershipBenefitDTO;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.membershipservice.membership.service.MembershipQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/memberships")
@RequiredArgsConstructor
public class InternalMembershipBenefitController {
    private final MembershipQueryService memberships;

    @GetMapping("/{userId}/benefit")
    public MembershipBenefitDTO activeBenefit(@PathVariable("userId") Long userId) {
        return memberships.activeBenefit(userId)
                .map(benefit -> new MembershipBenefitDTO(true, benefit.discountBasisPoints(),
                        benefit.membershipLevel(), benefit.expiresAt()))
                .orElseGet(() -> new MembershipBenefitDTO(false, null, null, null));
    }

    @GetMapping("/products/{productId}/reward")
    public MembershipRewardProductDTO getRewardProduct(@PathVariable("productId") long productId) {
        return memberships.getEnabledRewardProduct(productId);
    }
}
