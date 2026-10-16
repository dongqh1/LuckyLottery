package com.dongqh.luckyhub.service.payment;

import com.dongqh.luckyhub.domain.dto.payment.CreatePaymentCommand;
import com.dongqh.luckyhub.domain.dto.payment.PaymentCallbackCommand;
import com.dongqh.luckyhub.enums.payment.PaymentResult;
import com.dongqh.luckyhub.domain.vo.payment.PaymentView;

public interface PaymentService {
    PaymentView create(long userId, CreatePaymentCommand command);

    PaymentView callback(PaymentCallbackCommand command);

    String signForSimulation(String paymentNo, PaymentResult result, long amountCent);

    PaymentView simulate(String paymentNo, PaymentResult result, String failureReason);

    PaymentView simulateForUser(long userId, String paymentNo);
}
