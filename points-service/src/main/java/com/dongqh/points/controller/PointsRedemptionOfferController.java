package com.dongqh.points.controller;

import com.dongqh.points.service.points.PointsRedemptionOfferService;
import com.dongqh.points.domain.vo.points.PointsRedemptionOfferView;
import com.hmall.common.domain.R;
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
    public R<List<PointsRedemptionOfferView>> list() {
        return R.ok(offers.listAvailable());
    }
}

