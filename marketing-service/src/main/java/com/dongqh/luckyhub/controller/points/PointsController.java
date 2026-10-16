package com.dongqh.luckyhub.controller.points;

import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.domain.dto.points.PointsLedgerQuery;
import com.dongqh.luckyhub.service.points.PointsQueryService;
import com.dongqh.luckyhub.domain.vo.points.PointsAccountView;
import com.dongqh.luckyhub.domain.vo.points.PointsLedgerView;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/points")
public class PointsController {

    private final PointsQueryService service;

    public PointsController(PointsQueryService service) {
        this.service = service;
    }

    @GetMapping("/account")
    public ApiResponse<PointsAccountView> account() {
        return ApiResponse.success(service.getAccount(LoginContext.require().userId()));
    }

    @GetMapping("/ledgers")
    public ApiResponse<PageResponse<PointsLedgerView>> ledgers(
            @Valid @ModelAttribute PointsLedgerQuery query
    ) {
        return ApiResponse.success(service.pageLedgers(LoginContext.require().userId(), query));
    }
}

