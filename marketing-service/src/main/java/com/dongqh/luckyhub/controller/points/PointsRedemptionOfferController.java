package com.dongqh.luckyhub.controller.points;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.domain.dto.points.CreatePointsRedemptionOfferCommand;
import com.dongqh.luckyhub.service.points.PointsRedemptionOfferService;
import com.dongqh.luckyhub.domain.vo.points.PointsRedemptionOfferView;
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
    public ApiResponse<List<PointsRedemptionOfferView>> list() {
        return ApiResponse.success(service.listAvailable());
    }

    @PostMapping("/api/admin/points/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PointsRedemptionOfferView> create(
            @Valid @RequestBody CreatePointsRedemptionOfferCommand command
    ) {
        return ApiResponse.success(service.create(command));
    }
}

