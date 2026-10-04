package com.dongqh.fulfillmentservice.shipping;

import com.dongqh.fulfillmentservice.shipping.crypto.AesGcmAddressCipher;
import com.dongqh.fulfillmentservice.shipping.domain.ShippingDomainException;
import com.dongqh.fulfillmentservice.shipping.domain.ShippingErrorCode;
import com.dongqh.fulfillmentservice.shipping.enums.ShippingSourceType;
import com.dongqh.fulfillmentservice.shipping.model.ShippingAddressDetails;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingAddressSnapshot;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingAddressSnapshotRepository;
import com.dongqh.fulfillmentservice.shipping.port.ShippingAddressProvider;
import com.dongqh.fulfillmentservice.shipping.service.ShippingAddressSnapshotService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShippingAddressSnapshotServiceTests {

    @Test
    void storesOnlyEncryptedAddressAndReturnsMaskedProjection() {
        ShippingAddressSnapshotRepository repository = mock(ShippingAddressSnapshotRepository.class);
        ShippingAddressProvider addressProvider = mock(ShippingAddressProvider.class);
        AesGcmAddressCipher cipher = cipher();
        when(addressProvider.findAddressForUser(7L, 9L)).thenReturn(Optional.of(address()));
        when(repository.findBySource(ShippingSourceType.CASH_ORDER, "order-9"))
                .thenReturn(Optional.empty());
        when(repository.insert(any())).thenAnswer(invocation ->
                invocation.<ShippingAddressSnapshot>getArgument(0).withId(31L));

        ShippingAddressSnapshot snapshot = new ShippingAddressSnapshotService(repository, addressProvider, cipher)
                .create(7L, 9L, ShippingSourceType.CASH_ORDER, "order-9");

        assertThat(snapshot.id()).isEqualTo(31L);
        assertThat(snapshot.receiverMasked()).isEqualTo("张*");
        assertThat(snapshot.phoneMasked()).isEqualTo("138****8000");
        assertThat(snapshot.regionMasked()).isEqualTo("浙江省***");
        assertThat(snapshot.regionMasked()).doesNotContain("杭州市", "西湖区");
        assertThat(snapshot.receiverCiphertext()).isNotEqualTo("张三");
        assertThat(snapshot.toString()).doesNotContain("张三", "13800138000", "浙江省");
        assertThat(address().toString()).doesNotContain("13800138000", "路 1 号", "张三");
        assertThat(cipher.decrypt(snapshot.receiverCiphertext())).isEqualTo("张三");
    }

    @Test
    void rejectsAnAddressNotOwnedByTheRequestedUser() {
        ShippingAddressSnapshotRepository repository = mock(ShippingAddressSnapshotRepository.class);
        ShippingAddressProvider addressProvider = mock(ShippingAddressProvider.class);
        when(addressProvider.findAddressForUser(7L, 9L)).thenReturn(Optional.of(
                new ShippingAddressDetails(9L, 8L, "浙江省", "杭州市", "西湖区", "13800138000", "路 1 号", "张三")));

        assertThatThrownBy(() -> new ShippingAddressSnapshotService(repository, addressProvider, cipher())
                .create(7L, 9L, ShippingSourceType.CASH_ORDER, "order-9"))
                .isInstanceOf(ShippingDomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", ShippingErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void rejectsReusingSourceWithDifferentAddressContents() {
        AesGcmAddressCipher cipher = cipher();
        ShippingAddressSnapshotRepository repository = mock(ShippingAddressSnapshotRepository.class);
        ShippingAddressProvider addressProvider = mock(ShippingAddressProvider.class);
        ShippingAddressSnapshot existing = snapshot(cipher, "王五", "13800138000");
        when(addressProvider.findAddressForUser(7L, 9L)).thenReturn(Optional.of(address()));
        when(repository.findBySource(ShippingSourceType.CASH_ORDER, "order-9"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> new ShippingAddressSnapshotService(repository, addressProvider, cipher)
                .create(7L, 9L, ShippingSourceType.CASH_ORDER, "order-9"))
                .isInstanceOf(ShippingDomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
    }

    @Test
    void identicalSourceAndAddressRequestReturnsExistingSnapshotWithoutAnotherInsert() {
        AesGcmAddressCipher cipher = cipher();
        ShippingAddressSnapshotRepository repository = mock(ShippingAddressSnapshotRepository.class);
        ShippingAddressProvider addressProvider = mock(ShippingAddressProvider.class);
        ShippingAddressSnapshot existing = new ShippingAddressSnapshot(77L, "ADDRESS-77", 7L, 9L,
                ShippingSourceType.CASH_ORDER, "order-9", cipher.encrypt("张三"), cipher.encrypt("13800138000"),
                cipher.encrypt("浙江省"), cipher.encrypt("杭州市"), cipher.encrypt("西湖区"),
                cipher.encrypt("路 1 号"), "张*", "138****8000", "浙江省***", null);
        when(addressProvider.findAddressForUser(7L, 9L)).thenReturn(Optional.of(address()));
        when(repository.findBySource(ShippingSourceType.CASH_ORDER, "order-9"))
                .thenReturn(Optional.of(existing));

        ShippingAddressSnapshot result = new ShippingAddressSnapshotService(repository, addressProvider, cipher)
                .create(7L, 9L, ShippingSourceType.CASH_ORDER, "order-9");

        assertThat(result).isSameAs(existing);
        verify(repository, never()).insert(any());
    }

    private static ShippingAddressDetails address() {
        return new ShippingAddressDetails(9L, 7L, "浙江省", "杭州市", "西湖区", "13800138000", "路 1 号", "张三");
    }

    private static ShippingAddressSnapshot snapshot(AesGcmAddressCipher cipher, String receiver, String phone) {
        return new ShippingAddressSnapshot(1L, "ADDRESS-1", 7L, 9L, ShippingSourceType.CASH_ORDER, "order-9",
                cipher.encrypt(receiver), cipher.encrypt(phone), cipher.encrypt("浙江省"), cipher.encrypt("杭州市"),
                cipher.encrypt("西湖区"), cipher.encrypt("路 1 号"), "王*", "138****8000", "浙江省***", null);
    }

    private static AesGcmAddressCipher cipher() {
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        return new AesGcmAddressCipher("v1", Base64.getEncoder().encodeToString(key));
    }
}
