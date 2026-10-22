package com.dongqh.hmapi.client;

import com.dongqh.hmapi.dto.MembershipBenefitDTO;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.hmapi.dto.MembershipRewardGrantRequest;
import com.dongqh.hmapi.dto.MembershipPurchaseSnapshotDTO;
import com.dongqh.hmapi.dto.CreateMembershipRewardProductRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("membership-service")
public interface MembershipClient {
    @GetMapping("/internal/memberships/{userId}/benefit")
    MembershipBenefitDTO activeBenefit(@PathVariable("userId") Long userId);

    @GetMapping("/internal/memberships/products/{productId}/reward")
    MembershipRewardProductDTO getRewardProduct(@PathVariable("productId") long productId);

    @PostMapping("/internal/memberships/products/reward")
    MembershipRewardProductDTO createRewardProduct(@RequestBody CreateMembershipRewardProductRequest request);

    @GetMapping("/internal/memberships/products/{productId}/purchase")
    MembershipPurchaseSnapshotDTO getPurchaseSnapshot(@PathVariable("productId") long productId);

    @PostMapping("/internal/memberships/rewards/grant")
    void grantReward(@RequestBody MembershipRewardGrantRequest request);
}
