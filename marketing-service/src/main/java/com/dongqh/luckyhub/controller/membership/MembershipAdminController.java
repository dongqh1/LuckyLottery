package com.dongqh.luckyhub.controller.membership;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.domain.dto.membership.CreateMembershipProductCommand;
import com.dongqh.luckyhub.service.membership.MembershipService;
import com.dongqh.luckyhub.domain.vo.membership.MembershipProductView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/membership-products")
public class MembershipAdminController {
    private final MembershipService service;
    public MembershipAdminController(MembershipService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MembershipProductView> create(@Valid @RequestBody CreateMembershipProductCommand command) {
        return ApiResponse.success(service.createProduct(command));
    }
}

