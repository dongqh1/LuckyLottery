package com.dongqh.fulfillmentservice.service.shipping;

import com.dongqh.fulfillmentservice.integration.crypto.shipping.AddressCipher;
import com.dongqh.fulfillmentservice.domain.shipping.ShippingDomainException;
import com.dongqh.fulfillmentservice.domain.shipping.ShippingErrorCode;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillmentservice.integration.shipping.UserServiceShippingAddressProvider;
import com.dongqh.fulfillmentservice.domain.model.shipping.ShippingAddressDetails;
import com.dongqh.fulfillmentservice.persistence.shipping.DuplicateShippingAddressSnapshotException;
import com.dongqh.fulfillmentservice.persistence.shipping.ShippingAddressSnapshot;
import com.dongqh.fulfillmentservice.persistence.shipping.ShippingAddressSnapshotRepository;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class ShippingAddressSnapshotService {

    private final ShippingAddressSnapshotRepository repository;
    private final UserServiceShippingAddressProvider addresses;
    private final AddressCipher cipher;

    public ShippingAddressSnapshotService(
            ShippingAddressSnapshotRepository repository,
            UserServiceShippingAddressProvider addresses,
            AddressCipher cipher
    ) {
        this.repository = repository;
        this.addresses = addresses;
        this.cipher = cipher;
    }

    public ShippingAddressSnapshot create(long userId, long addressId, ShippingSourceType sourceType, String sourceId) {
        if (userId <= 0 || addressId <= 0 || sourceType == null || sourceId == null
                || sourceId.isBlank() || sourceId.trim().length() > 100) {
            throw error(ShippingErrorCode.ADDRESS_INVALID);
        }
        ShippingAddressDetails address = addresses.findAddressForUser(userId, addressId)
                .filter(candidate -> Objects.equals(candidate.id(), addressId)
                        && Objects.equals(candidate.userId(), userId))
                .orElseThrow(() -> error(ShippingErrorCode.ADDRESS_NOT_FOUND));
        ShippingAddressSnapshot candidate = copy(userId, addressId, sourceType, sourceId.trim(), address);

        return repository.findBySource(sourceType, candidate.sourceId())
                .map(existing -> requireEqual(existing, candidate))
                .orElseGet(() -> insertOrRequireWinner(candidate));
    }

    public ShippingAddressSnapshot require(long snapshotId) {
        if (snapshotId <= 0) {
            throw error(ShippingErrorCode.SHIPPING_NOT_FOUND);
        }
        return repository.findById(snapshotId).orElseThrow(() -> error(ShippingErrorCode.SHIPPING_NOT_FOUND));
    }

    public List<ShippingAddressSnapshot> requireByIds(Collection<Long> snapshotIds) {
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalized = snapshotIds.stream().distinct().toList();
        if (normalized.size() > 100 || normalized.stream().anyMatch(id -> id == null || id <= 0)) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return repository.findByIds(normalized);
    }

    private ShippingAddressSnapshot insertOrRequireWinner(ShippingAddressSnapshot candidate) {
        try {
            return repository.insert(candidate);
        } catch (DuplicateShippingAddressSnapshotException race) {
            return repository.findBySource(candidate.sourceType(), candidate.sourceId())
                    .map(existing -> requireEqual(existing, candidate))
                    .orElseThrow(() -> race);
        }
    }

    private ShippingAddressSnapshot copy(
            long userId,
            long addressId,
            ShippingSourceType sourceType,
            String sourceId,
            ShippingAddressDetails address
    ) {
        String receiver = required(address.contact());
        String phone = required(address.mobile());
        String province = required(address.province());
        String city = required(address.city());
        String district = required(address.town());
        String detail = required(address.street());
        if (!phone.matches("\\d{11}")) {
            throw error(ShippingErrorCode.ADDRESS_INVALID);
        }
        return new ShippingAddressSnapshot(null, "ADDRESS-" + UUID.randomUUID(), userId, addressId,
                sourceType, sourceId, cipher.encrypt(receiver), cipher.encrypt(phone), cipher.encrypt(province),
                cipher.encrypt(city), cipher.encrypt(district), cipher.encrypt(detail), maskReceiver(receiver),
                maskPhone(phone), province + "***", null);
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw error(ShippingErrorCode.ADDRESS_INVALID);
        }
        return value.trim();
    }

    private String maskReceiver(String receiver) {
        int codePoints = receiver.codePointCount(0, receiver.length());
        if (codePoints <= 1) {
            return "*";
        }
        int firstEnd = receiver.offsetByCodePoints(0, 1);
        return receiver.substring(0, firstEnd) + "*";
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private ShippingAddressSnapshot requireEqual(
            ShippingAddressSnapshot existing,
            ShippingAddressSnapshot candidate
    ) {
        boolean same = Objects.equals(existing.userId(), candidate.userId())
                && Objects.equals(existing.addressId(), candidate.addressId())
                && existing.sourceType() == candidate.sourceType()
                && Objects.equals(existing.sourceId(), candidate.sourceId())
                && samePlaintext(existing.receiverCiphertext(), candidate.receiverCiphertext())
                && samePlaintext(existing.phoneCiphertext(), candidate.phoneCiphertext())
                && samePlaintext(existing.provinceCiphertext(), candidate.provinceCiphertext())
                && samePlaintext(existing.cityCiphertext(), candidate.cityCiphertext())
                && samePlaintext(existing.districtCiphertext(), candidate.districtCiphertext())
                && samePlaintext(existing.detailCiphertext(), candidate.detailCiphertext())
                && Objects.equals(existing.receiverMasked(), candidate.receiverMasked())
                && Objects.equals(existing.phoneMasked(), candidate.phoneMasked())
                && Objects.equals(existing.regionMasked(), candidate.regionMasked());
        if (!same) {
            throw error(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
        }
        return existing;
    }

    private boolean samePlaintext(String left, String right) {
        return Objects.equals(cipher.decrypt(left), cipher.decrypt(right));
    }

    private ShippingDomainException error(ShippingErrorCode errorCode) {
        return new ShippingDomainException(errorCode);
    }
}
