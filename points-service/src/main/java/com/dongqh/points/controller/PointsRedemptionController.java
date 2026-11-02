package com.dongqh.points.controller;

import com.dongqh.points.domain.dto.points.CreatePointsRedemptionCommand;
import com.dongqh.points.domain.dto.points.PointsRedemptionQuery;
import com.dongqh.points.service.points.PointsRedemptionService;
import com.dongqh.points.domain.vo.points.PointsPageResponse;
import com.dongqh.points.domain.vo.points.PointsRedemptionView;
import com.hmall.common.domain.R;
import com.hmall.common.utils.UserContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/points/redemptions")
public class PointsRedemptionController {

    private final PointsRedemptionService redemptions;

    public PointsRedemptionController(PointsRedemptionService redemptions) {
        this.redemptions = redemptions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public R<PointsRedemptionView> create(
            @Valid @RequestBody CreatePointsRedemptionCommand command,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return R.ok(redemptions.create(requireUserId(), authorization, command));
    }

    @GetMapping
    public R<PointsPageResponse<PointsRedemptionView>> page(
            @Valid @ModelAttribute PointsRedemptionQuery query
    ) {
        return R.ok(redemptions.page(requireUserId(), query));
    }

    @GetMapping("/{redemptionNo}")
    public R<PointsRedemptionView> get(@PathVariable("redemptionNo") String redemptionNo) {
        return R.ok(redemptions.get(requireUserId(), redemptionNo));
    }

    private long requireUserId() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}

