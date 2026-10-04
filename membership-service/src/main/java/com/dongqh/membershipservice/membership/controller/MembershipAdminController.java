package com.dongqh.membershipservice.membership.controller;

import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import com.dongqh.membershipservice.membership.dto.CreateMembershipProductCommand;
import com.dongqh.membershipservice.membership.service.MembershipProductAdminService;
import com.dongqh.membershipservice.membership.vo.MembershipProductView;
import com.hmall.common.domain.R;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/membership-products")
public class MembershipAdminController {
    private final MembershipProductAdminService products;

    public MembershipAdminController(MembershipProductAdminService products) {
        this.products = products;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(ServicePermissionCodes.MEMBERSHIP_MANAGE)
    public R<MembershipProductView> create(@Valid @RequestBody CreateMembershipProductCommand command) {
        return R.ok(products.create(command));
    }
}
