package com.dongqh.luckyhub.controller.order;

import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.domain.dto.order.*;
import com.dongqh.luckyhub.service.order.CashOrderService;
import com.dongqh.luckyhub.domain.vo.order.CashOrderView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class CashOrderController {
    private final CashOrderService service;
    public CashOrderController(CashOrderService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CashOrderView> create(@Valid @RequestBody CreateCashOrderCommand command) {
        return ApiResponse.success(service.create(LoginContext.require().userId(), command));
    }

    @GetMapping
    public ApiResponse<PageResponse<CashOrderView>> page(@Valid @ModelAttribute CashOrderQuery query) {
        return ApiResponse.success(service.page(LoginContext.require().userId(), query));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<CashOrderView> get(@PathVariable String orderNo) {
        return ApiResponse.success(service.get(LoginContext.require().userId(), orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<CashOrderView> cancel(@PathVariable String orderNo,
                                              @Valid @RequestBody CancelCashOrderCommand command) {
        return ApiResponse.success(service.cancel(LoginContext.require().userId(), orderNo, command.reason()));
    }
}

