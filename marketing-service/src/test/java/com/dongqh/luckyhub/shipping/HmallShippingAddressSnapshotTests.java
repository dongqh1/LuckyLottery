package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.integration.hmall.HmallAddressClient;
import com.dongqh.luckyhub.integration.hmall.HmallAddressView;
import com.dongqh.luckyhub.shipping.crypto.AddressCipher;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.mapper.ShippingAddressSnapshotMapper;
import com.dongqh.luckyhub.shipping.service.impl.ShippingAddressSnapshotServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HmallShippingAddressSnapshotTests {

    @Test
    void freezesOwnedHmallAddressWithoutConsultingLocalAddressTable() {
        ShippingAddressSnapshotMapper mapper = mock(ShippingAddressSnapshotMapper.class);
        HmallAddressClient hmall = mock(HmallAddressClient.class);
        AddressCipher cipher = mock(AddressCipher.class);
        when(hmall.getAddress(61)).thenReturn(new HmallAddressView(
                61L, "广东省", "广州市", "天河区", "13800138000", "体育西路1号", "张三", 1, null));
        when(cipher.encrypt(anyString())).thenAnswer(invocation -> "encrypted:" + invocation.getArgument(0));
        doAnswer(invocation -> {
            invocation.<ShippingAddressSnapshot>getArgument(0).setId(91L);
            return 1;
        }).when(mapper).insert(any(ShippingAddressSnapshot.class));

        ShippingAddressSnapshot snapshot = new ShippingAddressSnapshotServiceImpl(mapper, hmall, cipher)
                .create(1, 61, ShippingSourceType.LOTTERY_BENEFIT, "5");

        assertThat(snapshot.getId()).isEqualTo(91L);
        assertThat(snapshot.getAddressId()).isEqualTo(61L);
        assertThat(snapshot.getUserId()).isEqualTo(1L);
        assertThat(snapshot.getReceiverCiphertext()).isEqualTo("encrypted:张三");
        assertThat(snapshot.getDistrictCiphertext()).isEqualTo("encrypted:天河区");
        assertThat(snapshot.getPhoneMasked()).doesNotContain("13800138000");
        verify(hmall).getAddress(61);
    }
}
