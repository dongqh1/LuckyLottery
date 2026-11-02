package com.dongqh.points.controller;

import com.dongqh.points.domain.dto.points.PointsLedgerQuery;
import com.dongqh.points.service.points.PointsQueryService;
import com.dongqh.points.domain.vo.points.PointsAccountView;
import com.dongqh.points.domain.vo.points.PointsLedgerView;
import com.dongqh.points.domain.vo.points.PointsPageResponse;
import com.hmall.common.domain.R;
import com.hmall.common.utils.UserContext;
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

