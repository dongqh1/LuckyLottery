package com.dongqh.luckyhub.points.controller;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionOfferCommand;
import com.dongqh.luckyhub.points.service.PointsRedemptionOfferService;
import com.dongqh.luckyhub.points.vo.PointsRedemptionOfferView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
public class PointsRedemptionOfferController {
    private final PointsRedemptionOfferService service;

    public PointsRedemptionOfferController(PointsRedemptionOfferService service) {
        this.service = service;
    }

    @GetMapping("/api/points/offers")
    @RequirePermission(PermissionCodes.POINTS_READ)
    public ApiResponse<List<PointsRedemptionOfferView>> list() {
        return ApiResponse.success(service.listAvailable());
    }

    @PostMapping("/api/admin/points/offers")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.POINTS_ADJUST)
    public ApiResponse<PointsRedemptionOfferView> create(
            @Valid @RequestBody CreatePointsRedemptionOfferCommand command
    ) {
        return ApiResponse.success(service.create(command));
    }
}
