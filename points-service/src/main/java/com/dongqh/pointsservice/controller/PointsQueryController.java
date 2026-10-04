package com.dongqh.pointsservice.controller;

import com.dongqh.luckyhub.points.dto.PointsLedgerQuery;
import com.dongqh.luckyhub.points.service.PointsQueryService;
import com.dongqh.luckyhub.points.vo.PointsAccountView;
import com.dongqh.luckyhub.points.vo.PointsLedgerView;
import com.dongqh.luckyhub.points.vo.PointsPageResponse;
import com.hmall.common.domain.R;
import com.hmall.common.utils.UserContext;
import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/points")
@RequirePermission(ServicePermissionCodes.POINTS_READ)
public class PointsQueryController {

    private final PointsQueryService points;

    public PointsQueryController(PointsQueryService points) {
        this.points = points;
    }

    @GetMapping("/account")
    public R<PointsAccountView> account() {
        return R.ok(points.getAccount(requireUserId()));
    }

    @GetMapping("/ledgers")
    public R<PointsPageResponse<PointsLedgerView>> ledgers(@Valid @ModelAttribute PointsLedgerQuery query) {
        return R.ok(points.pageLedgers(requireUserId(), query));
    }

    private long requireUserId() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}
