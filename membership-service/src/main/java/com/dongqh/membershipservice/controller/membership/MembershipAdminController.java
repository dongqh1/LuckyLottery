package com.dongqh.membershipservice.controller.membership;

import com.dongqh.membershipservice.domain.dto.membership.CreateMembershipProductCommand;
import com.dongqh.membershipservice.service.membership.MembershipProductAdminService;
import com.dongqh.membershipservice.domain.vo.membership.MembershipProductView;
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
    public R<MembershipProductView> create(@Valid @RequestBody CreateMembershipProductCommand command) {
        return R.ok(products.create(command));
    }
}

