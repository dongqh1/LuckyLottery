package com.dongqh.luckyhub.controller.payment;

import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.domain.dto.payment.CreatePaymentCommand;
import com.dongqh.luckyhub.service.payment.PaymentService;
import com.dongqh.luckyhub.domain.vo.payment.PaymentView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentView> create(@Valid @RequestBody CreatePaymentCommand command) {
        return ApiResponse.success(service.create(LoginContext.require().userId(), command));
    }

    @PostMapping("/{paymentNo}/simulate-success")
    public ApiResponse<PaymentView> simulateSuccess(@PathVariable String paymentNo) {
        return ApiResponse.success(service.simulateForUser(LoginContext.require().userId(), paymentNo));
    }
}

