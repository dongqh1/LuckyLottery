package com.dongqh.pointsservice.controller;

import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import com.dongqh.luckyhub.points.dto.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.points.dto.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.service.PointsRedemptionService;
import com.dongqh.luckyhub.points.vo.PointsLedgerView;
import com.dongqh.luckyhub.points.vo.PointsRedemptionView;
import com.hmall.common.domain.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Size;

@RestController
@Validated
@RequestMapping("/api/admin/points")
public class PointsAdminController {
    private final PointsAccountService accounts;
    private final PointsRedemptionService redemptions;

    public PointsAdminController(PointsAccountService accounts, PointsRedemptionService redemptions) {
        this.accounts = accounts;
        this.redemptions = redemptions;
    }

    @PostMapping("/adjustments")
    @RequirePermission(ServicePermissionCodes.POINTS_ADJUST)
    public R<PointsLedgerView> adjust(@Valid @RequestBody AdminPointsAdjustmentCommand command) {
        return R.ok(accounts.adjust(command));
    }

    @PostMapping("/redemptions/{redemptionNo}/reverse")
    @RequirePermission(ServicePermissionCodes.POINTS_ADJUST)
    public R<PointsRedemptionView> reverse(
            @PathVariable("redemptionNo") @Size(min = 1, max = 64) String redemptionNo,
            @Valid @RequestBody ReversePointsRedemptionCommand command) {
        return R.ok(redemptions.reverse(redemptionNo, command));
    }
}
