package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.domain.hmall.integration.HmallAddressClient;
import com.dongqh.luckyhub.domain.hmall.integration.HmallAddressView;
import com.dongqh.luckyhub.domain.po.rbac.SysUser;
import com.dongqh.luckyhub.mapper.rbac.SysUserMapper;
import com.dongqh.luckyhub.domain.dto.shipping.CreateShippingAddressCommand;
import com.dongqh.luckyhub.domain.po.shipping.ShippingAddressSnapshot;
import com.dongqh.luckyhub.enums.shipping.ShippingErrorCode;
import com.dongqh.luckyhub.enums.shipping.ShippingSourceType;
import com.dongqh.luckyhub.service.shipping.ShippingAddressService;
import com.dongqh.luckyhub.service.shipping.ShippingAddressSnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class ShippingAddressSnapshotTests {
    @Autowired ShippingAddressService addresses;
    @Autowired ShippingAddressSnapshotService snapshots;
    @Autowired SysUserMapper userMapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean HmallAddressClient hmallAddresses;
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void clean() {
        userIds.forEach(id -> jdbc.update("DELETE FROM shipping_address_snapshot WHERE user_id=?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM user_shipping_address WHERE user_id=?", id));
        userIds.forEach(userMapper::deleteById);
        userIds.clear();
    }

    @Test
    void copiesCiphertextWithoutDecryptingAndRemainsImmutableAfterAddressChanges() {
        long userId = createUser();
        long addressId = addresses.create(userId, address("张三", "杭州市", "文一西路1号")).id();
        stubAddress(addressId, "张三", "13812345678", "杭州市", "文一西路1号");
        String sourceId = businessId("cash");

        ShippingAddressSnapshot created = snapshots.create(
                userId, addressId, ShippingSourceType.CASH_ORDER, sourceId);
        var originalCiphertexts = ciphertexts(created);
        var originalMasked = List.of(created.getReceiverMasked(), created.getPhoneMasked(), created.getRegionMasked());

        addresses.update(userId, addressId, new com.dongqh.luckyhub.domain.dto.shipping.UpdateShippingAddressCommand(
                "李四", "13912345678", "上海市", "上海市", "浦东新区", "世纪大道2号", true));
        addresses.delete(userId, addressId);
        ShippingAddressSnapshot unchanged = snapshots.require(created.getId());

        assertThat(ciphertexts(unchanged)).isEqualTo(originalCiphertexts);
        assertThat(List.of(unchanged.getReceiverMasked(), unchanged.getPhoneMasked(), unchanged.getRegionMasked()))
                .isEqualTo(originalMasked).containsExactly("张*", "138****5678", "浙江省杭州市余杭区***");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM shipping_address_snapshot
                WHERE user_id=? AND source_type=? AND source_id=?
                """, Integer.class, userId, ShippingSourceType.CASH_ORDER.name(), sourceId)).isOne();
    }

    @Test
    void duplicateSourceReturnsEqualSnapshotButRejectsChangedOriginOrContents() {
        long userId = createUser();
        long first = addresses.create(userId, address("张三", "杭州市", "文一西路1号")).id();
        long second = addresses.create(userId, address("李四", "杭州市", "文一西路2号")).id();
        stubAddress(first, "张三", "13812345678", "杭州市", "文一西路1号");
        stubAddress(second, "李四", "13812345678", "杭州市", "文一西路2号");
        String sourceId = businessId("points");
        ShippingAddressSnapshot created = snapshots.create(
                userId, first, ShippingSourceType.POINTS_REDEMPTION, sourceId);

        assertThat(snapshots.create(userId, first, ShippingSourceType.POINTS_REDEMPTION, sourceId).getId())
                .isEqualTo(created.getId());
        assertError(() -> snapshots.create(userId, second, ShippingSourceType.POINTS_REDEMPTION, sourceId));
        addresses.update(userId, first, new com.dongqh.luckyhub.domain.dto.shipping.UpdateShippingAddressCommand(
                "王五", "13712345678", "浙江省", "杭州市", "余杭区", "未来科技城3号", false));
        stubAddress(first, "王五", "13712345678", "杭州市", "未来科技城3号");
        assertError(() -> snapshots.create(userId, first, ShippingSourceType.POINTS_REDEMPTION, sourceId));
    }

    private void stubAddress(long id, String contact, String mobile, String city, String street) {
        when(hmallAddresses.getAddress(id)).thenReturn(new HmallAddressView(
                id, "浙江省", city, "余杭区", mobile, street, contact, 0, null));
    }

    private long createUser() {
        SysUser user = new SysUser();
        user.setUsername("snapshot-" + UUID.randomUUID());
        user.setPassword("test-password");
        user.setNickname("快照用户");
        user.setStatus(1);
        userMapper.insert(user);
        userIds.add(user.getId());
        return user.getId();
    }

    private CreateShippingAddressCommand address(String receiver, String city, String detail) {
        return new CreateShippingAddressCommand(receiver, "13812345678", "浙江省", city,
                "余杭区", detail, false);
    }

    private String businessId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private List<String> ciphertexts(ShippingAddressSnapshot snapshot) {
        return List.of(snapshot.getReceiverCiphertext(), snapshot.getPhoneCiphertext(),
                snapshot.getProvinceCiphertext(), snapshot.getCityCiphertext(),
                snapshot.getDistrictCiphertext(), snapshot.getDetailCiphertext());
    }

    private void assertError(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT));
    }
}
