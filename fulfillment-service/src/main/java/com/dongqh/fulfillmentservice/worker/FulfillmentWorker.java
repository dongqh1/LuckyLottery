package com.dongqh.fulfillmentservice.worker;

import com.dongqh.fulfillmentservice.enums.AttemptOperation;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.domain.model.CouponFulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentClaim;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.domain.model.LogisticsFulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.model.MembershipFulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.model.PointsFulfillmentPayload;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskStateService;
import com.dongqh.fulfillmentservice.service.port.CouponFulfillmentGateway;
import com.dongqh.fulfillmentservice.service.port.LogisticsFulfillmentGateway;
import com.dongqh.fulfillmentservice.service.port.MembershipFulfillmentGateway;
import com.dongqh.fulfillmentservice.service.port.PointsFulfillmentGateway;
import com.dongqh.fulfillmentservice.domain.shipping.ShippingDomainException;
import com.dongqh.fulfillmentservice.domain.shipping.ShippingErrorCode;
import com.dongqh.fulfillmentservice.integration.shipping.LogisticsRequestAssembler;
import com.dongqh.fulfillmentservice.domain.model.shipping.LogisticsFulfillmentRequest;
import com.dongqh.fulfillmentservice.service.shipping.ShippingOrderFulfillmentProjector;
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
    private final CouponFulfillmentGateway coupons;
    private final PointsFulfillmentGateway points;
    private final MembershipFulfillmentGateway memberships;
    private final LogisticsFulfillmentGateway logistics;
    private final LogisticsRequestAssembler logisticsAssembler;
    private final ShippingOrderFulfillmentProjector shippingProjector;
    private final ObjectMapper json;
    private final int batchSize;
    private final Duration leaseDuration;
    private final Duration compensationGrace;

    public FulfillmentWorker(
            FulfillmentTaskStateService state,
            CouponFulfillmentGateway coupons,
            PointsFulfillmentGateway points,
            MembershipFulfillmentGateway memberships,
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
        this.coupons = coupons;
        this.points = points;
        this.memberships = memberships;
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
        for (FulfillmentClaim claim : claims) {
            execute(claim);
        }
    }

    private void execute(FulfillmentClaim claim) {
        long startedAt = System.nanoTime();
        FulfillmentGatewayResult result;
        try {
            result = route(claim);
        } catch (ShippingDomainException exception) {
            result = classifyShippingException(exception);
        } catch (RuntimeException exception) {
            result = FulfillmentGatewayResult.retryable("GATEWAY_EXCEPTION", "Gateway调用异常");
        }
        long durationMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
        state.recordResult(claim, result, durationMs);
        if (claim.fulfillmentType() == FulfillmentType.LOGISTICS) {
            shippingProjector.projectFulfillmentState(claim.fulfillmentNo());
        }
    }

    private FulfillmentGatewayResult route(FulfillmentClaim claim) {
        if (claim.operation() == AttemptOperation.QUERY) {
            return switch (claim.fulfillmentType()) {
                case COUPON -> coupons.query(claim.fulfillmentNo());
                case POINTS -> points.query(claim.fulfillmentNo());
                case MEMBERSHIP -> memberships.query(claim.fulfillmentNo());
                case LOGISTICS -> logistics.query(claim.fulfillmentNo());
            };
        }
        try {
            return switch (claim.fulfillmentType()) {
                case COUPON -> coupons.execute(claim.fulfillmentNo(), claim.targetUserId(),
                        json.readValue(claim.requestPayload(), CouponFulfillmentPayload.class));
                case POINTS -> points.execute(claim.fulfillmentNo(), claim.targetUserId(),
                        json.readValue(claim.requestPayload(), PointsFulfillmentPayload.class));
                case MEMBERSHIP -> memberships.execute(claim.fulfillmentNo(), claim.targetUserId(),
                        json.readValue(claim.requestPayload(), MembershipFulfillmentPayload.class));
                case LOGISTICS -> logistics.execute(logisticsAssembler.assemble(claim,
                        json.readValue(claim.requestPayload(), LogisticsFulfillmentPayload.class)));
            };
        } catch (JacksonException exception) {
            return FulfillmentGatewayResult.permanent("INVALID_SNAPSHOT", "履约快照无法解析");
        }
    }

    private FulfillmentGatewayResult classifyShippingException(ShippingDomainException exception) {
        if (exception.errorCode() == ShippingErrorCode.SHIPPING_REQUEST_INVALID
                || exception.errorCode() == ShippingErrorCode.SHIPPING_NOT_FOUND) {
            return FulfillmentGatewayResult.permanent("INVALID_SNAPSHOT", "物流履约快照无法匹配");
        }
        return FulfillmentGatewayResult.retryable("GATEWAY_EXCEPTION", "Gateway调用异常");
    }

    private void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
    }
}
