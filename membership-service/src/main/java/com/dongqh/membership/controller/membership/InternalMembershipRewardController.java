package com.dongqh.membership.controller.membership;

import com.dongqh.hmapi.dto.MembershipRewardGrantRequest;
import com.dongqh.membership.service.membership.MembershipRewardGrantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/memberships/rewards")
public class InternalMembershipRewardController {
    private final MembershipRewardGrantService grants;

    public InternalMembershipRewardController(MembershipRewardGrantService grants) {
        this.grants = grants;
    }

    @PostMapping("/grant")
    public void grant(@RequestBody MembershipRewardGrantRequest request) {
        grants.grant(request.fulfillmentNo(), request.targetUserId(), request.membershipProductId(),
                request.membershipCode(), request.durationDays(), request.quantity());
    }
}
