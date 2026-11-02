package com.dongqh.fulfillment.shipping;

import com.dongqh.fulfillment.enums.AttemptOperation;
import com.dongqh.fulfillment.enums.FulfillmentType;
import com.dongqh.fulfillment.domain.model.FulfillmentClaim;
import com.dongqh.fulfillment.domain.model.LogisticsFulfillmentPayload;
import com.dongqh.fulfillment.integration.crypto.shipping.AddressCipher;
import com.dongqh.fulfillment.domain.shipping.ShippingDomainException;
import com.dongqh.fulfillment.domain.shipping.ShippingErrorCode;
import com.dongqh.fulfillment.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillment.enums.shipping.ShippingStatus;
import com.dongqh.fulfillment.integration.shipping.LogisticsRequestAssembler;
import com.dongqh.fulfillment.domain.model.shipping.LogisticsFulfillmentRequest;
import com.dongqh.fulfillment.persistence.shipping.ShippingAddressSnapshot;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrder;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrderRepository;
import com.dongqh.fulfillment.service.shipping.ShippingAddressSnapshotService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogisticsRequestAssemblerTests {

    @Test
    void assemblesTheVendorRequestOnlyAfterOrderAndSnapshotIdentityChecks() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingAddressSnapshotService snapshots = mock(ShippingAddressSnapshotService.class);
        AddressCipher cipher = mock(AddressCipher.class);
        ShippingOrder order = order("LOGISTICS-8", "BENEFIT-8", "SKU-8", 1);
        ShippingAddressSnapshot snapshot = snapshot("BENEFIT-8", "王*", "138****8000");
        when(orders.findById(8)).thenReturn(Optional.of(order));
        when(snapshots.require(3)).thenReturn(snapshot);
        when(cipher.decrypt("receiver-cipher")).thenReturn("王小明");
        when(cipher.decrypt("phone-cipher")).thenReturn("13812348000");
        when(cipher.decrypt("province-cipher")).thenReturn("浙江省");
        when(cipher.decrypt("city-cipher")).thenReturn("杭州市");
        when(cipher.decrypt("district-cipher")).thenReturn("西湖区");
        when(cipher.decrypt("detail-cipher")).thenReturn("文三路1号");
        LogisticsRequestAssembler assembler = new LogisticsRequestAssembler(orders, snapshots, cipher);

        LogisticsFulfillmentRequest request = assembler.assemble(claim("LOGISTICS-8"), payload());

        assertThat(request.fulfillmentNo()).isEqualTo("LOGISTICS-8");
        assertThat(request.shippingOrderId()).isEqualTo(8);
        assertThat(request.targetUserId()).isEqualTo(42);
        assertThat(request.skuCode()).isEqualTo("SKU-8");
        assertThat(request.receiver()).isEqualTo("王小明");
        assertThat(request.phone()).isEqualTo("13812348000");
        assertThat(request.province()).isEqualTo("浙江省");
        assertThat(request.detail()).isEqualTo("文三路1号");
        assertThat(request.regionMasked()).isEqualTo(LogisticsFulfillmentPayload.REDACTED_REGION);
    }

    @Test
    void rejectsOrderIdentityOrProductMismatchBeforeDecryptingAddress() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingAddressSnapshotService snapshots = mock(ShippingAddressSnapshotService.class);
        AddressCipher cipher = mock(AddressCipher.class);
        when(orders.findById(8)).thenReturn(Optional.of(order("OTHER-TASK", "BENEFIT-8", "SKU-8", 1)));
        LogisticsRequestAssembler assembler = new LogisticsRequestAssembler(orders, snapshots, cipher);

        assertThatThrownBy(() -> assembler.assemble(claim("LOGISTICS-8"), payload()))
                .isInstanceOf(ShippingDomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", ShippingErrorCode.SHIPPING_REQUEST_INVALID);

        verify(snapshots, never()).require(3);
        verify(cipher, never()).decrypt(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsSnapshotMismatchAndKeepsTheAddressEncrypted() {
        ShippingOrderRepository orders = mock(ShippingOrderRepository.class);
        ShippingAddressSnapshotService snapshots = mock(ShippingAddressSnapshotService.class);
        AddressCipher cipher = mock(AddressCipher.class);
        when(orders.findById(8)).thenReturn(Optional.of(order("LOGISTICS-8", "BENEFIT-8", "SKU-8", 1)));
        when(snapshots.require(3)).thenReturn(snapshot("BENEFIT-8", "王*", "139****8000"));
        LogisticsRequestAssembler assembler = new LogisticsRequestAssembler(orders, snapshots, cipher);

        assertThatThrownBy(() -> assembler.assemble(claim("LOGISTICS-8"), payload()))
                .isInstanceOf(ShippingDomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", ShippingErrorCode.SHIPPING_REQUEST_INVALID);

        verify(cipher, never()).decrypt(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void assembledRequestNeverPrintsAddressPlaintext() {
        LogisticsFulfillmentRequest request = new LogisticsFulfillmentRequest(
                "LOGISTICS-8", 42, 8, "SKU-8", 1, "王小明", "13812348000",
                "浙江省", "杭州市", "西湖区", "文三路1号", "王*", "138****8000",
                LogisticsFulfillmentPayload.REDACTED_REGION);

        assertThat(request.toString()).doesNotContain("王小明", "13812348000", "文三路1号");
        assertThat(request.toString()).contains("REDACTED");
    }

    private static LogisticsFulfillmentPayload payload() {
        return new LogisticsFulfillmentPayload(8, "SKU-8", 1, "王*", "138****8000",
                LogisticsFulfillmentPayload.REDACTED_REGION);
    }

    private static FulfillmentClaim claim(String fulfillmentNo) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 18, 0);
        return new FulfillmentClaim(1, fulfillmentNo, FulfillmentType.LOGISTICS, 42, "{}",
                AttemptOperation.EXECUTE, "lease", now, now.plusSeconds(30));
    }

    private static ShippingOrder order(String fulfillmentNo, String sourceId, String skuCode, int quantity) {
        return new ShippingOrder(8L, "SHIPPING-8", ShippingSourceType.LOTTERY_BENEFIT, sourceId, 42L, 3L,
                99L, skuCode, "奖品", null, quantity, fulfillmentNo, null, null, null, null,
                ShippingStatus.FULFILLING, null, null, null, null, null, null, 1,
                LocalDateTime.of(2026, 9, 27, 17, 0), LocalDateTime.of(2026, 9, 27, 17, 0));
    }

    private static ShippingAddressSnapshot snapshot(String sourceId, String receiverMasked, String phoneMasked) {
        return new ShippingAddressSnapshot(3L, "SNAPSHOT-3", 42L, 6L, ShippingSourceType.LOTTERY_BENEFIT,
                sourceId, "receiver-cipher", "phone-cipher", "province-cipher", "city-cipher", "district-cipher",
                "detail-cipher", receiverMasked, phoneMasked, "浙江省***",
                LocalDateTime.of(2026, 9, 27, 16, 0));
    }
}
