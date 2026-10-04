package com.dongqh.pointsservice.controller;

import com.dongqh.luckyhub.points.service.PointsRedemptionOfferService;
import com.dongqh.luckyhub.points.vo.PointsRedemptionOfferView;
import com.hmall.common.domain.R;
import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PointsRedemptionOfferController {

    private final PointsRedemptionOfferService offers;

    public PointsRedemptionOfferController(PointsRedemptionOfferService offers) {
        this.offers = offers;
    }

    @GetMapping("/api/points/offers")
    @RequirePermission(ServicePermissionCodes.POINTS_READ)
    public R<List<PointsRedemptionOfferView>> list() {
        return R.ok(offers.listAvailable());
    }
}
