package com.dongqh.luckyhub.controller.payment;

import com.dongqh.luckyhub.common.result.ApiResponse;
import com.dongqh.luckyhub.domain.dto.payment.PaymentCallbackCommand;
import com.dongqh.luckyhub.domain.dto.payment.SimulatePaymentCommand;
import com.dongqh.luckyhub.service.payment.PaymentService;
import com.dongqh.luckyhub.domain.vo.payment.PaymentView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payments")
public class PaymentSimulationController {
    private final PaymentService service;
    public PaymentSimulationController(PaymentService service) { this.service = service; }

    @PostMapping("/{paymentNo}/simulate")
    public ApiResponse<PaymentView> simulate(@PathVariable String paymentNo,
                                             @Valid @RequestBody SimulatePaymentCommand command) {
        // Amount is deliberately resolved by the service through a trusted payment record.
        return ApiResponse.success(service.simulate(paymentNo, command.result(), command.failureReason()));
    }
}

