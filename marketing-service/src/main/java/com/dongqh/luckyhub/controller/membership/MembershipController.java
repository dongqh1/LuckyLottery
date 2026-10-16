package com.dongqh.luckyhub.controller.membership;

import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.domain.dto.membership.PurchaseMembershipCommand;
import com.dongqh.luckyhub.domain.dto.membership.PurchaseMyMembershipCommand;
import com.dongqh.luckyhub.service.membership.MembershipService;
import com.dongqh.luckyhub.domain.vo.membership.MembershipProductView;
import com.dongqh.luckyhub.domain.vo.membership.UserMembershipView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {
    private final MembershipService service;
    public MembershipController(MembershipService service) { this.service = service; }

    @GetMapping("/products")
    public ApiResponse<List<MembershipProductView>> products() {
        return ApiResponse.success(service.listEnabledProducts());
    }

    @GetMapping("/me")
    public ApiResponse<UserMembershipView> mine() {
        return ApiResponse.success(service.getMine(LoginContext.require().userId()).orElse(null));
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserMembershipView> purchase(@Valid @RequestBody PurchaseMyMembershipCommand command) {
        long userId = LoginContext.require().userId();
        return ApiResponse.success(service.purchase(new PurchaseMembershipCommand(
                command.businessNo(), command.membershipProductId(), userId)));
    }
}

