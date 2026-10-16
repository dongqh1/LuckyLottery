package com.dongqh.fulfillmentservice.worker;

import com.dongqh.fulfillmentservice.enums.AttemptOperation;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.domain.model.CouponFulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentClaim;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentGatewayResult;
import com.dongqh.fulfillmentservice.domain.model.LogisticsFulfillmentPayload;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FulfillmentWorkerTests {

    private final FulfillmentTaskStateService state = mock(FulfillmentTaskStateService.class);
    private final CouponFulfillmentGateway coupons = mock(CouponFulfillmentGateway.class);
    private final PointsFulfillmentGateway points = mock(PointsFulfillmentGateway.class);
    private final MembershipFulfillmentGateway memberships = mock(MembershipFulfillmentGateway.class);
    private final LogisticsFulfillmentGateway logistics = mock(LogisticsFulfillmentGateway.class);
    private final LogisticsRequestAssembler logisticsAssembler = mock(LogisticsRequestAssembler.class);
    private final ShippingOrderFulfillmentProjector shippingProjector =
            mock(ShippingOrderFulfillmentProjector.class);
    private final ObjectMapper json = new ObjectMapper();

    private FulfillmentWorker worker;

    @BeforeEach
    void setUp() {
        worker = new FulfillmentWorker(state, coupons, points, memberships, logistics, logisticsAssembler,
                shippingProjector, json, 20, Duration.ofSeconds(30), Duration.ofSeconds(30));
    }

    @Test
    void messageExecutesOnlyTheNamedCouponTaskAndRecordsTheGatewayResult() throws Exception {
        CouponFulfillmentPayload payload = new CouponFulfillmentPayload("COUPON-20", 2);
        FulfillmentClaim claim = claim("TASK-1", FulfillmentType.COUPON, AttemptOperation.EXECUTE,
                json.writeValueAsString(payload));
        when(state.claimReady(" TASK-1 ", Duration.ofSeconds(30))).thenReturn(Optional.of(claim));
        FulfillmentGatewayResult result = FulfillmentGatewayResult.succeeded("GRANT-1");
        when(coupons.execute("TASK-1", 42, payload)).thenReturn(result);

        assertThat(worker.runOne(" TASK-1 ")).isTrue();

        verify(state).claimReady(" TASK-1 ", Duration.ofSeconds(30));
        verify(coupons).execute("TASK-1", 42, payload);
        verify(state).recordResult(eq(claim), eq(result), any(Long.class));
        verifyNoInteractions(points, memberships, logistics, shippingProjector);
    }

    @Test
    void missingOrNotReadyMessageIsAcknowledgableWithoutCallingAnyGateway() {
        when(state.claimReady("TASK-MISSING", Duration.ofSeconds(30))).thenReturn(Optional.empty());

        assertThat(worker.runOne("TASK-MISSING")).isFalse();

        verify(state, never()).recordResult(any(), any(), any(Long.class));
        verifyNoInteractions(coupons, points, memberships, logistics, shippingProjector);
    }

    @Test
    void reconciliationUsesTheTypeSpecificQueryInsteadOfRepeatingTheGrant() {
        FulfillmentClaim claim = claim("TASK-2", FulfillmentType.POINTS, AttemptOperation.QUERY, "{}");
        when(state.claimDue(20, Duration.ofSeconds(30))).thenReturn(List.of(claim));
        FulfillmentGatewayResult result = FulfillmentGatewayResult.notFound();
        when(points.query("TASK-2")).thenReturn(result);

        assertThat(worker.runBatch()).isOne();

        verify(points).query("TASK-2");
        verify(points, never()).execute(any(), any(Long.class), any());
        verify(state).recordResult(eq(claim), eq(result), any(Long.class));
        verifyNoInteractions(coupons, memberships, logistics, shippingProjector);
    }

    @Test
    void malformedSnapshotIsRecordedAsPermanentAndDoesNotLeakParserDetails() {
        FulfillmentClaim claim = claim("TASK-3", FulfillmentType.COUPON, AttemptOperation.EXECUTE,
                "{not valid json");
        when(state.claimReady("TASK-3", Duration.ofSeconds(30))).thenReturn(Optional.of(claim));

        assertThat(worker.runOne("TASK-3")).isTrue();

        org.mockito.ArgumentCaptor<FulfillmentGatewayResult> result =
                org.mockito.ArgumentCaptor.forClass(FulfillmentGatewayResult.class);
        verify(state).recordResult(eq(claim), result.capture(), any(Long.class));
        assertThat(result.getValue().errorCode()).isEqualTo("INVALID_SNAPSHOT");
        assertThat(result.getValue().safeMessage()).isEqualTo("履约快照无法解析");
        verifyNoInteractions(coupons, points, memberships, logistics, shippingProjector);
    }

    @Test
    void logisticsStateIsProjectedOnlyAfterResultIsRecorded() throws Exception {
        LogisticsFulfillmentPayload payload = new LogisticsFulfillmentPayload(
                8, "SKU-8", 1, "王*", "138****8000", LogisticsFulfillmentPayload.REDACTED_REGION);
        FulfillmentClaim claim = claim("LOGISTICS-8", FulfillmentType.LOGISTICS, AttemptOperation.EXECUTE,
                json.writeValueAsString(payload));
        when(state.claimReady("LOGISTICS-8", Duration.ofSeconds(30))).thenReturn(Optional.of(claim));
        FulfillmentGatewayResult result = FulfillmentGatewayResult.succeeded("WAYBILL-8");
        LogisticsFulfillmentRequest request = new LogisticsFulfillmentRequest(
                "LOGISTICS-8", 42, 8, "SKU-8", 1, "王小明", "13812348000", "浙江省", "杭州市",
                "西湖区", "文三路1号", "王*", "138****8000", LogisticsFulfillmentPayload.REDACTED_REGION);
        when(logisticsAssembler.assemble(claim, payload)).thenReturn(request);
        when(logistics.execute(request)).thenReturn(result);

        assertThat(worker.runOne("LOGISTICS-8")).isTrue();

        InOrder order = inOrder(state, shippingProjector);
        verify(logisticsAssembler).assemble(claim, payload);
        verify(logistics).execute(request);
        order.verify(state).recordResult(eq(claim), eq(result), any(Long.class));
        order.verify(shippingProjector).projectFulfillmentState("LOGISTICS-8");
        verifyNoInteractions(coupons, points, memberships);
    }

    @Test
    void unexpectedGatewayFailureBecomesARecoverableSafeResult() {
        CouponFulfillmentPayload payload = new CouponFulfillmentPayload("COUPON-20", 1);
        FulfillmentClaim claim = claim("TASK-4", FulfillmentType.COUPON, AttemptOperation.EXECUTE,
                "{\"couponTemplateCode\":\"COUPON-20\",\"quantity\":1}");
        when(state.claimReady("TASK-4", Duration.ofSeconds(30))).thenReturn(Optional.of(claim));
        when(coupons.execute("TASK-4", 42, payload)).thenThrow(new IllegalStateException("private endpoint token"));

        worker.runOne("TASK-4");

        org.mockito.ArgumentCaptor<FulfillmentGatewayResult> result =
                org.mockito.ArgumentCaptor.forClass(FulfillmentGatewayResult.class);
        verify(state).recordResult(eq(claim), result.capture(), any(Long.class));
        assertThat(result.getValue().errorCode()).isEqualTo("GATEWAY_EXCEPTION");
        assertThat(result.getValue().safeMessage()).isEqualTo("Gateway调用异常");
        assertThat(result.getValue().safeMessage()).doesNotContain("private endpoint token");
    }

    @Test
    void knownShippingSnapshotMismatchIsRecordedAsPermanentWithoutCallingTheGateway() throws Exception {
        LogisticsFulfillmentPayload payload = new LogisticsFulfillmentPayload(
                8, "SKU-8", 1, "王*", "138****8000", LogisticsFulfillmentPayload.REDACTED_REGION);
        FulfillmentClaim claim = claim("LOGISTICS-8", FulfillmentType.LOGISTICS, AttemptOperation.EXECUTE,
                json.writeValueAsString(payload));
        when(state.claimReady("LOGISTICS-8", Duration.ofSeconds(30))).thenReturn(Optional.of(claim));
        when(logisticsAssembler.assemble(claim, payload)).thenThrow(
                new ShippingDomainException(ShippingErrorCode.SHIPPING_REQUEST_INVALID));

        assertThat(worker.runOne("LOGISTICS-8")).isTrue();

        org.mockito.ArgumentCaptor<FulfillmentGatewayResult> result =
                org.mockito.ArgumentCaptor.forClass(FulfillmentGatewayResult.class);
        verify(state).recordResult(eq(claim), result.capture(), any(Long.class));
        assertThat(result.getValue().outcome()).isEqualTo(com.dongqh.fulfillmentservice.enums.GatewayOutcome.PERMANENT_FAILURE);
        assertThat(result.getValue().errorCode()).isEqualTo("INVALID_SNAPSHOT");
        assertThat(result.getValue().safeMessage()).isEqualTo("物流履约快照无法匹配");
        verifyNoInteractions(logistics);
    }

    private FulfillmentClaim claim(
            String fulfillmentNo,
            FulfillmentType type,
            AttemptOperation operation,
            String payload
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 12, 0);
        return new FulfillmentClaim(1, fulfillmentNo, type, 42, payload, operation,
                "lease-token", now, now.plusSeconds(30));
    }
}
