package com.dongqh.fulfillment.worker;

import com.dongqh.fulfillment.domain.model.FulfillmentClaim;
import com.dongqh.fulfillment.domain.model.FulfillmentGatewayResult;
import com.dongqh.fulfillment.domain.model.LogisticsFulfillmentPayload;
import com.dongqh.fulfillment.domain.model.shipping.LogisticsFulfillmentRequest;
import com.dongqh.fulfillment.domain.shipping.ShippingDomainException;
import com.dongqh.fulfillment.domain.shipping.ShippingErrorCode;
import com.dongqh.fulfillment.enums.AttemptOperation;
import com.dongqh.fulfillment.enums.FulfillmentType;
import com.dongqh.fulfillment.integration.shipping.LogisticsRequestAssembler;
import com.dongqh.fulfillment.service.FulfillmentTaskStateService;
import com.dongqh.fulfillment.service.port.LogisticsFulfillmentGateway;
import com.dongqh.fulfillment.service.shipping.ShippingOrderFulfillmentProjector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FulfillmentWorker {

    private final FulfillmentTaskStateService state;
    private final LogisticsFulfillmentGateway logistics;
    private final LogisticsRequestAssembler logisticsAssembler;
    private final ShippingOrderFulfillmentProjector shippingProjector;
    private final ObjectMapper json;
    private final int batchSize;
    private final Duration leaseDuration;
    private final Duration compensationGrace;

    public FulfillmentWorker(
            FulfillmentTaskStateService state,
            LogisticsFulfillmentGateway logistics,
            LogisticsRequestAssembler logisticsAssembler,
            ShippingOrderFulfillmentProjector shippingProjector,
            ObjectMapper json,
            @Value("${luckyhub.fulfillment.batch-size:100}") int batchSize,
            @Value("${luckyhub.fulfillment.lease-duration:30s}") Duration leaseDuration,
            @Value("${luckyhub.fulfillment.compensation-grace:30s}") Duration compensationGrace
    ) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize必须为1到1000");
        }
        requirePositive(leaseDuration, "leaseDuration");
        requirePositive(compensationGrace, "compensationGrace");
        this.state = state;
        this.logistics = logistics;
        this.logisticsAssembler = logisticsAssembler;
        this.shippingProjector = shippingProjector;
        this.json = json;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.compensationGrace = compensationGrace;
    }

    public int runBatch() {
        var claims = state.claimDue(batchSize, leaseDuration);
        execute(claims);
        return claims.size();
    }

    public boolean runOne(String fulfillmentNo) {
        Optional<FulfillmentClaim> claim = state.claimReady(fulfillmentNo, leaseDuration);
        claim.ifPresent(this::execute);
        return claim.isPresent();
    }

    public int runCompensationBatch() {
        var claims = state.claimCompensationDue(batchSize, leaseDuration, compensationGrace);
        execute(claims);
        return claims.size();
    }

    private void execute(Iterable<FulfillmentClaim> claims) {
        for (FulfillmentClaim claim : claims) execute(claim);
    }

    private void execute(FulfillmentClaim claim) {
        long startedAt = System.nanoTime();
        FulfillmentGatewayResult result;
        try {
            result = route(claim);
        } catch (ShippingDomainException exception) {
            result = classifyShippingException(exception);
        } catch (RuntimeException exception) {
            result = FulfillmentGatewayResult.retryable("GATEWAY_EXCEPTION", "物流网关调用异常");
        }
        long durationMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
        state.recordResult(claim, result, durationMs);
        if (claim.fulfillmentType() == FulfillmentType.LOGISTICS) {
            shippingProjector.projectFulfillmentState(claim.fulfillmentNo());
        }
    }

    private FulfillmentGatewayResult route(FulfillmentClaim claim) {
        if (claim.fulfillmentType() != FulfillmentType.LOGISTICS) {
            return FulfillmentGatewayResult.permanent(
                    "UNSUPPORTED_TASK_TYPE", "物流服务只处理实物发货任务");
        }
        if (claim.operation() == AttemptOperation.QUERY) {
            return logistics.query(claim.fulfillmentNo());
        }
        try {
            LogisticsFulfillmentPayload payload = json.readValue(
                    claim.requestPayload(), LogisticsFulfillmentPayload.class);
            LogisticsFulfillmentRequest request = logisticsAssembler.assemble(claim, payload);
            return logistics.execute(request);
        } catch (JacksonException exception) {
            return FulfillmentGatewayResult.permanent("INVALID_SNAPSHOT", "物流请求数据无效");
        }
    }

    private FulfillmentGatewayResult classifyShippingException(ShippingDomainException exception) {
        if (exception.errorCode() == ShippingErrorCode.SHIPPING_REQUEST_INVALID
                || exception.errorCode() == ShippingErrorCode.SHIPPING_NOT_FOUND) {
            return FulfillmentGatewayResult.permanent("INVALID_SNAPSHOT", "物流请求与发货单信息不匹配");
        }
        return FulfillmentGatewayResult.retryable("GATEWAY_EXCEPTION", "物流网关调用异常");
    }

    private void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
    }
}
