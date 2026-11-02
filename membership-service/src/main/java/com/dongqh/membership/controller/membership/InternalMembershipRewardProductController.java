package com.dongqh.membership.controller.membership;

import com.dongqh.hmapi.dto.CreateMembershipRewardProductRequest;
import com.dongqh.hmapi.dto.MembershipRewardProductDTO;
import com.dongqh.membership.service.membership.MembershipRewardProductAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/memberships/products")
public class InternalMembershipRewardProductController {
    private final MembershipRewardProductAdminService products;

    public InternalMembershipRewardProductController(MembershipRewardProductAdminService products) {
        this.products = products;
    }

    @PostMapping("/reward")
    public MembershipRewardProductDTO createRewardProduct(
            @Valid @RequestBody CreateMembershipRewardProductRequest command) {
        return products.create(command);
    }
}
