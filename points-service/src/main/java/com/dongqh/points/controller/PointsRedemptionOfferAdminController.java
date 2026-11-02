package com.dongqh.points.controller;

import com.dongqh.points.domain.dto.points.CreatePointsRedemptionOfferCommand;
import com.dongqh.points.service.points.PointsRedemptionOfferService;
import com.dongqh.points.domain.vo.points.PointsRedemptionOfferView;
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
    public R<PointsRedemptionOfferView> create(@Valid @RequestBody CreatePointsRedemptionOfferCommand command) {
        return R.ok(offers.create(command));
    }
}

