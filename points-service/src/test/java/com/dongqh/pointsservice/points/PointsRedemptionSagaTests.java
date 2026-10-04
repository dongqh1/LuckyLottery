package com.dongqh.pointsservice.points;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dongqh.hmapi.client.FulfillmentClient;
import com.dongqh.hmapi.dto.CreateFulfillmentShippingOrderRequest;
import com.dongqh.hmapi.dto.FulfillmentShippingOrderDTO;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.points.dto.PointsMutationCommand;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOrder;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.enums.PointsProductType;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStatus;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStep;
import com.dongqh.luckyhub.points.exception.PointsDomainException;
import com.dongqh.luckyhub.points.mapper.PointsRedemptionOrderMapper;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.service.PointsRedemptionOfferService;
import com.dongqh.luckyhub.points.service.PointsRedemptionStateService;
import com.dongqh.luckyhub.points.service.impl.PointsRedemptionServiceImpl;
import com.dongqh.luckyhub.points.service.port.PointsInventoryPort;
import com.dongqh.luckyhub.points.service.port.PointsRedeemableSkuPort;
import com.dongqh.luckyhub.points.service.port.PointsRedeemableSkuSnapshot;
import com.dongqh.luckyhub.points.service.port.PointsUserEligibilityPort;
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

    @Mock private PointsUserEligibilityPort userEligibility;
    @Mock private PointsRedeemableSkuPort skuCatalog;
    @Mock private PointsInventoryPort inventory;
    @Mock private PointsAccountService accounts;
    @Mock private PointsRedemptionOfferService offers;
    @Mock private PointsRedemptionOrderMapper orders;
    @Mock private FulfillmentClient fulfillment;

    private final AtomicReference<PointsRedemptionOrder> currentOrder = new AtomicReference<>();
    private PointsRedemptionServiceImpl service;

    @BeforeEach
    void setUp() {
        currentOrder.set(null);
        PointsRedemptionStateService state = new PointsRedemptionStateService(orders, offers);
        service = new PointsRedemptionServiceImpl(userEligibility, skuCatalog, inventory, accounts,
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

        when(userEligibility.isEnabled(7L)).thenReturn(true);
        when(skuCatalog.getRedeemableSku(33L)).thenReturn(Optional.of(physicalSku()));
    }

    @Test
    void completesPhysicalRedemptionAfterFulfillmentPersistsShippingOrder() {
        FulfillmentShippingOrderDTO accepted = acceptedShippingOrder();
        when(fulfillment.createShippingOrder(anyString(), any(CreateFulfillmentShippingOrderRequest.class)))
                .thenReturn(accepted);
        when(fulfillment.getShippingOrdersByIds(List.of(701L))).thenReturn(List.of(accepted));

        var result = service.create(7L, "Bearer trusted-user", command("R-1"));

        assertThat(result.status()).isEqualTo(PointsRedemptionStatus.COMPLETED);
        assertThat(result.shippingOrderId()).isEqualTo(701L);
        assertThat(result.addressSnapshot()).extracting("receiverMasked").isEqualTo("张*");
        assertThat(currentOrder.get().getProcessStep()).isEqualTo(PointsRedemptionStep.COMPLETED);

        verify(inventory).reserve(33L, 1, "R-1");
        verify(accounts).debit(any(PointsMutationCommand.class));
        verify(inventory).confirm("R-1");
        ArgumentCaptor<CreateFulfillmentShippingOrderRequest> request =
                ArgumentCaptor.forClass(CreateFulfillmentShippingOrderRequest.class);
        verify(fulfillment).createShippingOrder(org.mockito.ArgumentMatchers.eq("Bearer trusted-user"),
                request.capture());
        assertThat(request.getValue().sourceType()).isEqualTo("POINTS_REDEMPTION");
        assertThat(request.getValue().sourceId()).isEqualTo("900");
        assertThat(request.getValue().addressId()).isEqualTo(55L);
        verifyNoInteractions(offers);
    }

    @Test
    void compensatesAndHidesRedemptionWhenFulfillmentRejectsAfterPointsDebit() {
        when(fulfillment.createShippingOrder(anyString(), any(CreateFulfillmentShippingOrderRequest.class)))
                .thenThrow(new PointsDomainException(PointsErrorCode.REDEMPTION_ADDRESS_INVALID));

        assertThatThrownBy(() -> service.create(7L, "Bearer trusted-user", command("R-2")))
                .isInstanceOfSatisfying(PointsDomainException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PointsErrorCode.REDEMPTION_ADDRESS_INVALID));

        assertThat(currentOrder.get().getStatus()).isEqualTo(PointsRedemptionStatus.COMPENSATED);
        verify(accounts).reverseDebit(org.mockito.ArgumentMatchers.argThat(reversal ->
                reversal.originalBusinessId().equals("R-2")
                        && reversal.reversalBusinessId().equals("R-2:COMPENSATE")));
        verify(inventory).compensate("R-2");
        assertThatThrownBy(() -> service.get(7L, "R-2"))
                .isInstanceOfSatisfying(PointsDomainException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PointsErrorCode.REDEMPTION_NOT_FOUND));
        assertThatThrownBy(() -> service.create(7L, "Bearer trusted-user", command("R-2")))
                .isInstanceOfSatisfying(PointsDomainException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PointsErrorCode.REDEMPTION_ADDRESS_INVALID));
    }

    @Test
    void keepsProcessingAndResumesCompensationAfterTemporaryPointsReversalFailure() {
        when(fulfillment.createShippingOrder(anyString(), any(CreateFulfillmentShippingOrderRequest.class)))
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
        verify(inventory).compensate("R-3");
    }

    private CreatePointsRedemptionCommand command(String redemptionNo) {
        return new CreatePointsRedemptionCommand(redemptionNo, 33L, null, 1, 55L);
    }

    private PointsRedeemableSkuSnapshot physicalSku() {
        return new PointsRedeemableSkuSnapshot(33L, "SKU-P-33", "蓝牙耳机", "S-33", "黑色",
                PointsProductType.PHYSICAL.name(), "/images/33.png", 120L);
    }

    private FulfillmentShippingOrderDTO acceptedShippingOrder() {
        return new FulfillmentShippingOrderDTO(701L, "SH-701", "FUL-701", "CREATED", 801L, "ADDR-801",
                null, null, null, null, null, null, null, null, null, "张*", "138****", "上海***");
    }
}
