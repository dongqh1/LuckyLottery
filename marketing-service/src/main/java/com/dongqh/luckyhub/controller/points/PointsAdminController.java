package com.dongqh.luckyhub.controller.points;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.domain.dto.points.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.domain.dto.points.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.service.points.PointsAccountService;
import com.dongqh.luckyhub.service.points.PointsRedemptionService;
import com.dongqh.luckyhub.domain.vo.points.PointsLedgerView;
import com.dongqh.luckyhub.domain.vo.points.PointsRedemptionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/points")
public class PointsAdminController {

    private final PointsAccountService accountService;
    private final PointsRedemptionService redemptionService;

    public PointsAdminController(
            PointsAccountService accountService,
            PointsRedemptionService redemptionService
    ) {
        this.accountService = accountService;
        this.redemptionService = redemptionService;
    }

    @PostMapping("/adjustments")
    public ApiResponse<PointsLedgerView> adjust(
            @Valid @RequestBody AdminPointsAdjustmentCommand command
    ) {
        return ApiResponse.success(accountService.adjust(command));
    }

    @PostMapping("/redemptions/{redemptionNo}/reverse")
    public ApiResponse<PointsRedemptionView> reverse(
            @PathVariable @Size(min = 1, max = 64) String redemptionNo,
            @Valid @RequestBody ReversePointsRedemptionCommand command
    ) {
        return ApiResponse.success(redemptionService.reverse(redemptionNo, command));
    }
}

