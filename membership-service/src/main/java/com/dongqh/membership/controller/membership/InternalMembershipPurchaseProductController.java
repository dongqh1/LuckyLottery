package com.dongqh.membership.controller.membership;

import com.dongqh.hmapi.dto.MembershipPurchaseSnapshotDTO;
import com.dongqh.membership.service.membership.MembershipQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/memberships/products")
public class InternalMembershipPurchaseProductController {
    private final MembershipQueryService queries;

    public InternalMembershipPurchaseProductController(MembershipQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/{productId}/purchase")
    public MembershipPurchaseSnapshotDTO getPurchaseSnapshot(@PathVariable long productId) {
        return queries.getEnabledPurchaseSnapshot(productId);
    }
}
