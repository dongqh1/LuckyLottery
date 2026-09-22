package com.dongqh.luckyhub.drawchance.controller;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.drawchance.service.DrawChanceService;
import com.dongqh.luckyhub.drawchance.vo.DrawChanceAccountView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lottery/chances")
@Tag(name = "抽奖次数", description = "当前商城用户的可用抽奖次数")
public class DrawChanceController {
    private final DrawChanceService service;

    public DrawChanceController(DrawChanceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询我的剩余抽奖次数")
    @RequirePermission(PermissionCodes.LOTTERY_ACTIVITY_READ)
    public ApiResponse<DrawChanceAccountView> mine() {
        return ApiResponse.success(service.get(LoginContext.require().userId()));
    }
}
