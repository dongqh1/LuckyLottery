package com.dongqh.pointsservice.controller;

import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionOfferCommand;
import com.dongqh.luckyhub.points.service.PointsRedemptionOfferService;
import com.dongqh.luckyhub.points.vo.PointsRedemptionOfferView;
import com.hmall.common.domain.R;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/points/offers")
public class PointsRedemptionOfferAdminController {
    private final PointsRedemptionOfferService offers;

    public PointsRedemptionOfferAdminController(PointsRedemptionOfferService offers) {
        this.offers = offers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(ServicePermissionCodes.POINTS_ADJUST)
    public R<PointsRedemptionOfferView> create(@Valid @RequestBody CreatePointsRedemptionOfferCommand command) {
        return R.ok(offers.create(command));
    }
}
