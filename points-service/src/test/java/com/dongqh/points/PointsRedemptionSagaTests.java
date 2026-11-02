package com.dongqh.points;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dongqh.hmapi.client.LogisticsClient;
import com.dongqh.hmapi.dto.CreateShippingOrderRequest;
import com.dongqh.hmapi.dto.ShippingOrderDTO;
import com.dongqh.points.domain.dto.points.CreatePointsRedemptionCommand;
import com.dongqh.points.domain.dto.points.PointsMutationCommand;
import com.dongqh.points.domain.po.points.PointsRedemptionOrder;
import com.dongqh.points.enums.points.PointsErrorCode;
import com.dongqh.points.enums.points.PointsProductType;
import com.dongqh.points.enums.points.PointsRedemptionStatus;
import com.dongqh.points.enums.points.PointsRedemptionStep;
import com.dongqh.points.domain.exception.PointsDomainException;
import com.dongqh.points.integration.PointsIntegrationService;
import com.dongqh.points.mapper.points.PointsRedemptionOrderMapper;
import com.dongqh.points.service.points.PointsAccountService;
import com.dongqh.points.service.points.PointsRedemptionOfferService;
import com.dongqh.points.service.points.PointsRedemptionStateService;
import com.dongqh.points.service.points.impl.PointsRedemptionServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class PointsRedemptionSagaTests {

    @Mock private PointsIntegrationService integration;
    @Mock private PointsAccountService accounts;
    @Mock private PointsRedemptionOfferService offers;
    @Mock private PointsRedemptionOrderMapper orders;
    @Mock private LogisticsClient fulfillment;

    private final AtomicReference<PointsRedemptionOrder> currentOrder = new AtomicReference<>();
    private PointsRedemptionServiceImpl service;

    @BeforeEach
    void setUp() {
        currentOrder.set(null);
        PointsRedemptionStateService state = new PointsRedemptionStateService(orders, offers);
        service = new PointsRedemptionServiceImpl(integration, accounts,
                offers, state, orders, fulfillment);

        lenient().when(orders.selectOne(any(Wrapper.class))).thenAnswer(invocation -> currentOrder.get());
        lenient().when(orders.lockByRedemptionNo(anyString())).thenAnswer(invocation -> currentOrder.get());
        lenient().when(orders.claim(any(PointsRedemptionOrder.class))).thenAnswer(invocation -> {
            PointsRedemptionOrder claimed = invocation.getArgument(0);
            claimed.setId(900L);
            currentOrder.set(claimed);
            return 1;
        });
        lenient().when(orders.advanceStep(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            PointsRedemptionOrder order = currentOrder.get();
            if (order == null || order.getStatus() != PointsRedemptionStatus.PROCESSING
                    || !order.getProcessStep().name().equals(invocation.getArgument(1))) {
                return 0;
            }
            order.setProcessStep(PointsRedemptionStep.valueOf(invocation.getArgument(2)));
            return 1;
        });
        lenient().when(orders.attachShippingOrder(anyString(), anyLong())).thenAnswer(invocation -> {
            PointsRedemptionOrder order = currentOrder.get();
            if (order == null || order.getStatus() != PointsRedemptionStatus.PROCESSING
                    || order.getProcessStep() != PointsRedemptionStep.SKU_CONFIRMED
                    || order.getShippingOrderId() != null) {
                return 0;
            }
            order.setShippingOrderId(invocation.getArgument(1));
            order.setProcessStep(PointsRedemptionStep.FULFILLMENT_ACCEPTED);
            return 1;
        });
        lenient().when(orders.complete(anyString(), anyString(), nullable(Long.class))).thenAnswer(invocation -> {
            PointsRedemptionOrder order = currentOrder.get();
            if (order == null || order.getStatus() != PointsRedemptionStatus.PROCESSING
                    || !order.getProcessStep().name().equals(invocation.getArgument(1))) {
                return 0;
            }
            order.setStatus(PointsRedemptionStatus.COMPLETED);
            order.setProcessStep(PointsRedemptionStep.COMPLETED);
            order.setShippingOrderId(invocation.getArgument(2));
            return 1;
        });
        lenient().when(orders.setCompensation(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    PointsRedemptionOrder order = currentOrder.get();
                    if (order == null || order.getStatus() != PointsRedemptionStatus.PROCESSING
                            || !order.getProcessStep().name().equals(invocation.getArgument(1))) {
                        return 0;
                    }
                    order.setProcessStep(PointsRedemptionStep.valueOf(invocation.getArgument(2)));
                    order.setOriginalErrorCode(invocation.getArgument(3));
                    order.setFailureReason(invocation.getArgument(4));
                    return 1;
                });
        lenient().when(orders.compensate(anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            PointsRedemptionOrder order = currentOrder.get();
            if (order == null || order.getStatus() != PointsRedemptionStatus.PROCESSING
                    || !order.getProcessStep().name().equals(invocation.getArgument(1))) {
                return 0;
            }
            order.setStatus(PointsRedemptionStatus.COMPENSATED);
            order.setProcessStep(PointsRedemptionStep.COMPENSATED);
            order.setOriginalErrorCode(invocation.getArgument(2));
            order.setFailureReason(invocation.getArgument(3));
            return 1;
        });

        when(integration.isEnabledUser(7L)).thenReturn(true);
        when(integration.getRedeemableSku(33L)).thenReturn(Optional.of(physicalSku()));
    }

    @Test
    void completesPhysicalRedemptionAfterFulfillmentPersistsShippingOrder() {
        ShippingOrderDTO accepted = acceptedShippingOrder();
        when(fulfillment.createShippingOrder(anyString(), any(CreateShippingOrderRequest.class)))
                .thenReturn(accepted);
        when(fulfillment.getShippingOrdersByIds(List.of(701L))).thenReturn(List.of(accepted));

        var result = service.create(7L, "Bearer trusted-user", command("R-1"));

        assertThat(result.status()).isEqualTo(PointsRedemptionStatus.COMPLETED);
        assertThat(result.shippingOrderId()).isEqualTo(701L);
        assertThat(result.addressSnapshot()).extracting("receiverMasked").isEqualTo("张*");
        assertThat(currentOrder.get().getProcessStep()).isEqualTo(PointsRedemptionStep.COMPLETED);

        verify(integration).reserve(33L, 1, "R-1");
        verify(accounts).debit(any(PointsMutationCommand.class));
        verify(integration).confirm("R-1");
        ArgumentCaptor<CreateShippingOrderRequest> request =
                ArgumentCaptor.forClass(CreateShippingOrderRequest.class);
        verify(fulfillment).createShippingOrder(org.mockito.ArgumentMatchers.eq("Bearer trusted-user"),
                request.capture());
        assertThat(request.getValue().sourceType()).isEqualTo("POINTS_REDEMPTION");
        assertThat(request.getValue().sourceId()).isEqualTo("900");
        assertThat(request.getValue().addressId()).isEqualTo(55L);
        verifyNoInteractions(offers);
    }

    @Test
    void compensatesAndHidesRedemptionWhenFulfillmentRejectsAfterPointsDebit() {
        when(fulfillment.createShippingOrder(anyString(), any(CreateShippingOrderRequest.class)))
                .thenThrow(new PointsDomainException(PointsErrorCode.REDEMPTION_ADDRESS_INVALID));

        assertThatThrownBy(() -> service.create(7L, "Bearer trusted-user", command("R-2")))
                .isInstanceOfSatisfying(PointsDomainException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PointsErrorCode.REDEMPTION_ADDRESS_INVALID));

        assertThat(currentOrder.get().getStatus()).isEqualTo(PointsRedemptionStatus.COMPENSATED);
        verify(accounts).reverseDebit(org.mockito.ArgumentMatchers.argThat(reversal ->
                reversal.originalBusinessId().equals("R-2")
                        && reversal.reversalBusinessId().equals("R-2:COMPENSATE")));
        verify(integration).compensate("R-2");
        assertThatThrownBy(() -> service.get(7L, "R-2"))
                .isInstanceOfSatisfying(PointsDomainException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PointsErrorCode.REDEMPTION_NOT_FOUND));
        assertThatThrownBy(() -> service.create(7L, "Bearer trusted-user", command("R-2")))
                .isInstanceOfSatisfying(PointsDomainException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PointsErrorCode.REDEMPTION_ADDRESS_INVALID));
    }

    @Test
    void keepsProcessingAndResumesCompensationAfterTemporaryPointsReversalFailure() {
        when(fulfillment.createShippingOrder(anyString(), any(CreateShippingOrderRequest.class)))
                .thenThrow(new PointsDomainException(PointsErrorCode.REDEMPTION_ADDRESS_INVALID));
        doThrow(new IllegalStateException("points ledger temporarily unavailable"))
                .doReturn(null)
                .when(accounts).reverseDebit(any());

        assertThatThrownBy(() -> service.create(7L, "Bearer trusted-user", command("R-3")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(currentOrder.get().getStatus()).isEqualTo(PointsRedemptionStatus.PROCESSING);
        assertThat(currentOrder.get().getProcessStep()).isEqualTo(PointsRedemptionStep.COMPENSATING_POINTS);

        assertThatThrownBy(() -> service.create(7L, "Bearer trusted-user", command("R-3")))
                .isInstanceOfSatisfying(PointsDomainException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PointsErrorCode.REDEMPTION_ADDRESS_INVALID));

        assertThat(currentOrder.get().getStatus()).isEqualTo(PointsRedemptionStatus.COMPENSATED);
        verify(accounts, org.mockito.Mockito.times(2)).reverseDebit(any());
        verify(integration).compensate("R-3");
    }

    private CreatePointsRedemptionCommand command(String redemptionNo) {
        return new CreatePointsRedemptionCommand(redemptionNo, 33L, null, 1, 55L);
    }

    private PointsIntegrationService.RedeemableSkuSnapshot physicalSku() {
        return new PointsIntegrationService.RedeemableSkuSnapshot(33L, "SKU-P-33", "蓝牙耳机", "S-33", "黑色",
                PointsProductType.PHYSICAL.name(), "/images/33.png", 120L);
    }

    private ShippingOrderDTO acceptedShippingOrder() {
        return new ShippingOrderDTO(701L, "SH-701", "FUL-701", "CREATED", 801L, "ADDR-801",
                null, null, null, null, null, null, null, null, null, "张*", "138****", "上海***");
    }
}
