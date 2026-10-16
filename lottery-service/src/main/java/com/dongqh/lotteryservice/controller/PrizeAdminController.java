package com.dongqh.lotteryservice.controller;

import com.dongqh.lotteryservice.domain.dto.prizecatalog.CreatePrizeCommand;
import com.dongqh.lotteryservice.domain.dto.prizecatalog.PrizeQuery;
import com.dongqh.lotteryservice.domain.dto.prizecatalog.UpdatePrizeCommand;
import com.dongqh.lotteryservice.service.prizecatalog.PrizeCatalogService;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.PageResponse;
import com.dongqh.lotteryservice.domain.vo.prizecatalog.PrizeView;
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
    public R<PrizeView> create(@Valid @RequestBody CreatePrizeCommand command) {
        return R.ok(prizes.create(command));
    }

    @GetMapping("/{id}")
    public R<PrizeView> get(@PathVariable("id") @Positive long id) {
        return R.ok(prizes.getById(id));
    }

    @GetMapping
    public R<PageResponse<PrizeView>> page(@Valid PrizeQuery query) {
        return R.ok(prizes.page(query));
    }

    @PutMapping("/{id}")
    public R<PrizeView> update(@PathVariable("id") @Positive long id,
                               @Valid @RequestBody UpdatePrizeCommand command) {
        return R.ok(prizes.update(id, command));
    }

    @PatchMapping("/{id}/disable")
    public R<Void> disable(@PathVariable("id") @Positive long id) {
        prizes.disable(id);
        return R.ok();
    }
}

