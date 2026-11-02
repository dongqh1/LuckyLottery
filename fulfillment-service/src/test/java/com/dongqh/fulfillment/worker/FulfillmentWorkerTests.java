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
    private final LogisticsFulfillmentGateway logistics = mock(LogisticsFulfillmentGateway.class);
    private final LogisticsRequestAssembler logisticsAssembler = mock(LogisticsRequestAssembler.class);
    private final ShippingOrderFulfillmentProjector shippingProjector =
            mock(ShippingOrderFulfillmentProjector.class);
    private final ObjectMapper json = new ObjectMapper();
    private FulfillmentWorker worker;

    @BeforeEach
    void setUp() {
        worker = new FulfillmentWorker(state, logistics, logisticsAssembler, shippingProjector,
                json, 20, Duration.ofSeconds(30), Duration.ofSeconds(30));
    }

    @Test
    void logisticsTaskIsExecutedAndProjectedAfterItsResultIsRecorded() throws Exception {
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
    }

    @Test
    void missingOrNotReadyMessageDoesNotCallAnyLogisticsDependency() {
        when(state.claimReady("SHIPPING-MISSING", Duration.ofSeconds(30))).thenReturn(Optional.empty());

        assertThat(worker.runOne("SHIPPING-MISSING")).isFalse();

        verify(state, never()).recordResult(any(), any(), any(Long.class));
        verifyNoInteractions(logistics, logisticsAssembler, shippingProjector);
    }

    @Test
    void reconciliationQueriesTheLogisticsGatewayInsteadOfRepeatingShipmentCreation() {
        FulfillmentClaim claim = claim("LOGISTICS-9", FulfillmentType.LOGISTICS, AttemptOperation.QUERY, "{}");
        when(state.claimDue(20, Duration.ofSeconds(30))).thenReturn(List.of(claim));
        FulfillmentGatewayResult result = FulfillmentGatewayResult.notFound();
        when(logistics.query("LOGISTICS-9")).thenReturn(result);

        assertThat(worker.runBatch()).isOne();

        verify(logistics).query("LOGISTICS-9");
        verify(logistics, never()).execute(any());
        verify(state).recordResult(eq(claim), eq(result), any(Long.class));
        verify(shippingProjector).projectFulfillmentState("LOGISTICS-9");
    }

    @Test
    void malformedShippingPayloadIsRecordedWithoutLeakingParserDetails() {
        FulfillmentClaim claim = claim("LOGISTICS-10", FulfillmentType.LOGISTICS, AttemptOperation.EXECUTE,
                "{not valid json");
        when(state.claimReady("LOGISTICS-10", Duration.ofSeconds(30))).thenReturn(Optional.of(claim));

        assertThat(worker.runOne("LOGISTICS-10")).isTrue();

        var result = org.mockito.ArgumentCaptor.forClass(FulfillmentGatewayResult.class);
        verify(state).recordResult(eq(claim), result.capture(), any(Long.class));
        assertThat(result.getValue().errorCode()).isEqualTo("INVALID_SNAPSHOT");
        assertThat(result.getValue().safeMessage()).isEqualTo("物流请求数据无效");
        verifyNoInteractions(logistics);
    }

    @Test
    void unsupportedLegacyRewardTaskDoesNotReachAnyRewardOrLogisticsGateway() {
        FulfillmentClaim claim = claim("LEGACY-11", FulfillmentType.COUPON, AttemptOperation.EXECUTE, "{}");
        when(state.claimReady("LEGACY-11", Duration.ofSeconds(30))).thenReturn(Optional.of(claim));

        assertThat(worker.runOne("LEGACY-11")).isTrue();

        var result = org.mockito.ArgumentCaptor.forClass(FulfillmentGatewayResult.class);
        verify(state).recordResult(eq(claim), result.capture(), any(Long.class));
        assertThat(result.getValue().errorCode()).isEqualTo("UNSUPPORTED_TASK_TYPE");
        assertThat(result.getValue().outcome()).isEqualTo(
                com.dongqh.fulfillment.enums.GatewayOutcome.PERMANENT_FAILURE);
        verifyNoInteractions(logistics, logisticsAssembler, shippingProjector);
    }

    @Test
    void knownShippingSnapshotMismatchIsRecordedAsPermanentWithoutCallingTheGateway() throws Exception {
        LogisticsFulfillmentPayload payload = new LogisticsFulfillmentPayload(
                8, "SKU-8", 1, "王*", "138****8000", LogisticsFulfillmentPayload.REDACTED_REGION);
        FulfillmentClaim claim = claim("LOGISTICS-12", FulfillmentType.LOGISTICS, AttemptOperation.EXECUTE,
                json.writeValueAsString(payload));
        when(state.claimReady("LOGISTICS-12", Duration.ofSeconds(30))).thenReturn(Optional.of(claim));
        when(logisticsAssembler.assemble(claim, payload)).thenThrow(
                new ShippingDomainException(ShippingErrorCode.SHIPPING_REQUEST_INVALID));

        assertThat(worker.runOne("LOGISTICS-12")).isTrue();

        var result = org.mockito.ArgumentCaptor.forClass(FulfillmentGatewayResult.class);
        verify(state).recordResult(eq(claim), result.capture(), any(Long.class));
        assertThat(result.getValue().outcome()).isEqualTo(
                com.dongqh.fulfillment.enums.GatewayOutcome.PERMANENT_FAILURE);
        assertThat(result.getValue().errorCode()).isEqualTo("INVALID_SNAPSHOT");
        assertThat(result.getValue().safeMessage()).isEqualTo("物流请求与发货单信息不匹配");
        verifyNoInteractions(logistics);
    }

    @Test
    void unexpectedLogisticsGatewayFailureIsRetryableAndSafe() throws Exception {
        LogisticsFulfillmentPayload payload = new LogisticsFulfillmentPayload(
                8, "SKU-8", 1, "王*", "138****8000", LogisticsFulfillmentPayload.REDACTED_REGION);
        FulfillmentClaim claim = claim("LOGISTICS-13", FulfillmentType.LOGISTICS, AttemptOperation.EXECUTE,
                json.writeValueAsString(payload));
        LogisticsFulfillmentRequest request = new LogisticsFulfillmentRequest(
                "LOGISTICS-13", 42, 8, "SKU-8", 1, "王小明", "13812348000", "浙江省", "杭州市",
                "西湖区", "文三路1号", "王*", "138****8000", LogisticsFulfillmentPayload.REDACTED_REGION);
        when(state.claimReady("LOGISTICS-13", Duration.ofSeconds(30))).thenReturn(Optional.of(claim));
        when(logisticsAssembler.assemble(claim, payload)).thenReturn(request);
        when(logistics.execute(request)).thenThrow(new IllegalStateException("private endpoint token"));

        worker.runOne("LOGISTICS-13");

        var result = org.mockito.ArgumentCaptor.forClass(FulfillmentGatewayResult.class);
        verify(state).recordResult(eq(claim), result.capture(), any(Long.class));
        assertThat(result.getValue().errorCode()).isEqualTo("GATEWAY_EXCEPTION");
        assertThat(result.getValue().safeMessage()).isEqualTo("物流网关调用异常");
        assertThat(result.getValue().safeMessage()).doesNotContain("private endpoint token");
    }

    private FulfillmentClaim claim(String no, FulfillmentType type, AttemptOperation operation, String payload) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 12, 0);
        return new FulfillmentClaim(1, no, type, 42, payload, operation,
                "lease-token", now, now.plusSeconds(30));
    }
}
