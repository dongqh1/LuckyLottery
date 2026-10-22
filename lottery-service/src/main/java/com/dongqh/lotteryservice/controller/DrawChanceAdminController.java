package com.dongqh.lotteryservice.controller;

import com.dongqh.lotteryservice.domain.dto.drawchance.AdjustDrawChanceCommand;
import com.dongqh.lotteryservice.domain.vo.drawchance.DrawChanceAccountView;
import com.dongqh.lotteryservice.domain.vo.drawchance.DrawChanceAdjustmentView;
import com.dongqh.lotteryservice.domain.vo.drawchance.DrawChanceLedgerView;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.PageResponse;
import com.dongqh.lotteryservice.security.LotteryAdminAuthorization;
import com.dongqh.lotteryservice.service.drawchance.DrawChanceAdminService;
import com.hmall.common.domain.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/draw-chances")
public class DrawChanceAdminController {
    private final DrawChanceAdminService chances;
    private final LotteryAdminAuthorization authorization;

    public DrawChanceAdminController(DrawChanceAdminService chances, LotteryAdminAuthorization authorization) {
        this.chances = chances;
        this.authorization = authorization;
    }

    @GetMapping("/users/{userId}")
    public R<DrawChanceAccountView> account(@PathVariable("userId") @Positive long userId) {
        authorization.requireAdmin();
        return R.ok(chances.getAccount(userId));
    }

    @GetMapping("/users/{userId}/ledger")
    public R<PageResponse<DrawChanceLedgerView>> ledger(
            @PathVariable("userId") @Positive long userId,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        authorization.requireAdmin();
        return R.ok(chances.pageLedger(userId, page, size));
    }

    @PostMapping("/users/{userId}/adjustments")
    public R<DrawChanceAdjustmentView> adjust(@PathVariable("userId") @Positive long userId,
                                               @Valid @RequestBody AdjustDrawChanceCommand command) {
        long operatorUserId = authorization.requireAdmin();
        return R.ok(chances.adjust(userId, operatorUserId, command));
    }
}
