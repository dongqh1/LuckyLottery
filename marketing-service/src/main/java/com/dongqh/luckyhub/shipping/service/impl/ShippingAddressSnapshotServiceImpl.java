package com.dongqh.luckyhub.shipping.service.impl;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.integration.hmall.HmallAddressClient;
import com.dongqh.luckyhub.integration.hmall.HmallAddressView;
import com.dongqh.luckyhub.shipping.crypto.AddressCipher;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.mapper.ShippingAddressSnapshotMapper;
import com.dongqh.luckyhub.shipping.service.ShippingAddressSnapshotService;
import com.dongqh.luckyhub.shipping.support.AddressMasker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class ShippingAddressSnapshotServiceImpl implements ShippingAddressSnapshotService {
    private final ShippingAddressSnapshotMapper mapper;
    private final HmallAddressClient addresses;
    private final AddressCipher cipher;

    public ShippingAddressSnapshotServiceImpl(
            ShippingAddressSnapshotMapper mapper,
            HmallAddressClient addresses,
            AddressCipher cipher
    ) {
        this.mapper = mapper;
        this.addresses = addresses;
        this.cipher = cipher;
    }

    @Override
    public ShippingAddressSnapshot create(
            long userId,
            long addressId,
            ShippingSourceType sourceType,
            String sourceId
    ) {
        HmallAddressView address = addresses.getAddress(addressId);
        if (address == null || !Objects.equals(address.id(), addressId)) {
            throw new BusinessException(ShippingErrorCode.ADDRESS_NOT_FOUND);
        }
        ShippingAddressSnapshot candidate = copy(userId, addressId, sourceType, sourceId, address);
        ShippingAddressSnapshot existing = mapper.selectBySource(sourceType, sourceId);
        if (existing != null) {
            return requireEqual(existing, candidate);
        }
        try {
            mapper.insert(candidate);
            return candidate;
        } catch (DuplicateKeyException exception) {
            ShippingAddressSnapshot winner = mapper.selectBySource(sourceType, sourceId);
            if (winner == null) {
                throw exception;
            }
            return requireEqual(winner, candidate);
        }
    }

    @Override
    public ShippingAddressSnapshot require(long snapshotId) {
        ShippingAddressSnapshot snapshot = mapper.selectById(snapshotId);
        if (snapshot == null) {
            throw new BusinessException(ShippingErrorCode.SHIPPING_NOT_FOUND);
        }
        return snapshot;
    }

    private ShippingAddressSnapshot copy(
            long userId,
            long addressId,
            ShippingSourceType sourceType,
            String sourceId,
            HmallAddressView address
    ) {
        ShippingAddressSnapshot snapshot = new ShippingAddressSnapshot();
        snapshot.setSnapshotNo("ADDRESS-" + UUID.randomUUID());
        snapshot.setUserId(userId);
        snapshot.setAddressId(addressId);
        snapshot.setSourceType(sourceType);
        snapshot.setSourceId(sourceId);
        snapshot.setReceiverCiphertext(cipher.encrypt(required(address.contact())));
        snapshot.setPhoneCiphertext(cipher.encrypt(required(address.mobile())));
        snapshot.setProvinceCiphertext(cipher.encrypt(required(address.province())));
        snapshot.setCityCiphertext(cipher.encrypt(required(address.city())));
        snapshot.setDistrictCiphertext(cipher.encrypt(required(address.town())));
        snapshot.setDetailCiphertext(cipher.encrypt(required(address.street())));
        snapshot.setReceiverMasked(AddressMasker.maskReceiver(address.contact()));
        snapshot.setPhoneMasked(AddressMasker.maskPhone(address.mobile()));
        snapshot.setRegionMasked(AddressMasker.maskRegion(address.province(), address.city(), address.town()));
        return snapshot;
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ShippingErrorCode.ADDRESS_INVALID);
        }
        return value.trim();
    }

    private ShippingAddressSnapshot requireEqual(
            ShippingAddressSnapshot existing,
            ShippingAddressSnapshot candidate
    ) {
        if (!Objects.equals(existing.getUserId(), candidate.getUserId())
                || !Objects.equals(existing.getAddressId(), candidate.getAddressId())
                || existing.getSourceType() != candidate.getSourceType()
                || !Objects.equals(existing.getSourceId(), candidate.getSourceId())
                || !samePlaintext(existing.getReceiverCiphertext(), candidate.getReceiverCiphertext())
                || !samePlaintext(existing.getPhoneCiphertext(), candidate.getPhoneCiphertext())
                || !samePlaintext(existing.getProvinceCiphertext(), candidate.getProvinceCiphertext())
                || !samePlaintext(existing.getCityCiphertext(), candidate.getCityCiphertext())
                || !samePlaintext(existing.getDistrictCiphertext(), candidate.getDistrictCiphertext())
                || !samePlaintext(existing.getDetailCiphertext(), candidate.getDetailCiphertext())
                || !Objects.equals(existing.getReceiverMasked(), candidate.getReceiverMasked())
                || !Objects.equals(existing.getPhoneMasked(), candidate.getPhoneMasked())
                || !Objects.equals(existing.getRegionMasked(), candidate.getRegionMasked())) {
            throw new BusinessException(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
        }
        return existing;
    }

    private boolean samePlaintext(String leftCiphertext, String rightCiphertext) {
        return Objects.equals(cipher.decrypt(leftCiphertext), cipher.decrypt(rightCiphertext));
    }
}
