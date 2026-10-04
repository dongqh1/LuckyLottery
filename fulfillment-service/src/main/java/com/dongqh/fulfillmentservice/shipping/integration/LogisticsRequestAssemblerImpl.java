package com.dongqh.fulfillmentservice.shipping.integration;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.model.FulfillmentClaim;
import com.dongqh.fulfillmentservice.model.LogisticsFulfillmentPayload;
import com.dongqh.fulfillmentservice.shipping.crypto.AddressCipher;
import com.dongqh.fulfillmentservice.shipping.domain.ShippingDomainException;
import com.dongqh.fulfillmentservice.shipping.domain.ShippingErrorCode;
import com.dongqh.fulfillmentservice.shipping.model.LogisticsFulfillmentRequest;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingAddressSnapshot;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingOrder;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingOrderRepository;
import com.dongqh.fulfillmentservice.shipping.service.ShippingAddressSnapshotService;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class LogisticsRequestAssemblerImpl implements LogisticsRequestAssembler {

    private final ShippingOrderRepository orders;
    private final ShippingAddressSnapshotService snapshots;
    private final AddressCipher cipher;

    public LogisticsRequestAssemblerImpl(
            ShippingOrderRepository orders,
            ShippingAddressSnapshotService snapshots,
            AddressCipher cipher
    ) {
        this.orders = orders;
        this.snapshots = snapshots;
        this.cipher = cipher;
    }

    @Override
    public LogisticsFulfillmentRequest assemble(FulfillmentClaim claim, LogisticsFulfillmentPayload payload) {
        if (claim == null || payload == null || claim.fulfillmentType() != FulfillmentType.LOGISTICS) {
            throw invalidRequest();
        }
        ShippingOrder order = orders.findById(payload.shippingOrderId()).orElse(null);
        if (order == null
                || !Objects.equals(order.fulfillmentNo(), claim.fulfillmentNo())
                || !Objects.equals(order.targetUserId(), claim.targetUserId())
                || !Objects.equals(order.skuCode(), payload.skuCode())
                || !Objects.equals(order.quantity(), payload.quantity())) {
            throw invalidRequest();
        }

        ShippingAddressSnapshot snapshot = snapshots.require(order.addressSnapshotId());
        if (!Objects.equals(snapshot.userId(), order.targetUserId())
                || snapshot.sourceType() != order.sourceType()
                || !Objects.equals(snapshot.sourceId(), order.sourceId())
                || !Objects.equals(snapshot.receiverMasked(), payload.receiverMasked())
                || !Objects.equals(snapshot.phoneMasked(), payload.phoneMasked())) {
            throw invalidRequest();
        }

        // The fulfillment payload intentionally carries only a generic region mask; the order view retains
        // the snapshot's province mask. Do not copy the province mask into the external request envelope.
        return new LogisticsFulfillmentRequest(
                claim.fulfillmentNo(),
                claim.targetUserId(),
                order.id(),
                payload.skuCode(),
                payload.quantity(),
                cipher.decrypt(snapshot.receiverCiphertext()),
                cipher.decrypt(snapshot.phoneCiphertext()),
                cipher.decrypt(snapshot.provinceCiphertext()),
                cipher.decrypt(snapshot.cityCiphertext()),
                cipher.decrypt(snapshot.districtCiphertext()),
                cipher.decrypt(snapshot.detailCiphertext()),
                payload.receiverMasked(),
                payload.phoneMasked(),
                payload.regionMasked());
    }

    private ShippingDomainException invalidRequest() {
        return new ShippingDomainException(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
    }
}
