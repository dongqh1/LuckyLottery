package com.dongqh.lotteryservice.controller;

import com.dongqh.hmsecurity.permission.RequirePermission;
import com.dongqh.hmsecurity.permission.ServicePermissionCodes;
import com.dongqh.lotteryservice.prizecatalog.dto.CreatePrizeCommand;
import com.dongqh.lotteryservice.prizecatalog.dto.PrizeQuery;
import com.dongqh.lotteryservice.prizecatalog.dto.UpdatePrizeCommand;
import com.dongqh.lotteryservice.prizecatalog.service.PrizeCatalogService;
import com.dongqh.lotteryservice.prizecatalog.vo.PageResponse;
import com.dongqh.lotteryservice.prizecatalog.vo.PrizeView;
import com.hmall.common.domain.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/prizes")
public class PrizeAdminController {
    private final PrizeCatalogService prizes;

    public PrizeAdminController(PrizeCatalogService prizes) {
        this.prizes = prizes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(ServicePermissionCodes.PRIZE_CREATE)
    public R<PrizeView> create(@Valid @RequestBody CreatePrizeCommand command) {
        return R.ok(prizes.create(command));
    }

    @GetMapping("/{id}")
    @RequirePermission(ServicePermissionCodes.PRIZE_READ)
    public R<PrizeView> get(@PathVariable("id") @Positive long id) {
        return R.ok(prizes.getById(id));
    }

    @GetMapping
    @RequirePermission(ServicePermissionCodes.PRIZE_READ)
    public R<PageResponse<PrizeView>> page(@Valid PrizeQuery query) {
        return R.ok(prizes.page(query));
    }

    @PutMapping("/{id}")
    @RequirePermission(ServicePermissionCodes.PRIZE_UPDATE)
    public R<PrizeView> update(@PathVariable("id") @Positive long id,
                               @Valid @RequestBody UpdatePrizeCommand command) {
        return R.ok(prizes.update(id, command));
    }

    @PatchMapping("/{id}/disable")
    @RequirePermission(ServicePermissionCodes.PRIZE_DISABLE)
    public R<Void> disable(@PathVariable("id") @Positive long id) {
        prizes.disable(id);
        return R.ok();
    }
}
