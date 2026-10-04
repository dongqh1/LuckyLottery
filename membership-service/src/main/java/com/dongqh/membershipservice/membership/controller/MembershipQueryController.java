package com.dongqh.membershipservice.membership.controller;

import com.dongqh.membershipservice.membership.service.MembershipQueryService;
import com.dongqh.membershipservice.membership.vo.MembershipProductView;
import com.dongqh.membershipservice.membership.vo.UserMembershipView;
import com.hmall.common.domain.R;
import com.hmall.common.utils.UserContext;
import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/memberships")
@RequirePermission(ServicePermissionCodes.MEMBERSHIP_READ)
public class MembershipQueryController {

    private final MembershipQueryService memberships;

    public MembershipQueryController(MembershipQueryService memberships) {
        this.memberships = memberships;
    }

    @GetMapping("/products")
    public R<List<MembershipProductView>> products() {
        requireUserId();
        return R.ok(memberships.listEnabledProducts());
    }

    @GetMapping("/me")
    public R<UserMembershipView> mine() {
        Long userId = requireUserId();
        return R.ok(memberships.getMine(userId).orElse(null));
    }

    private Long requireUserId() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}
