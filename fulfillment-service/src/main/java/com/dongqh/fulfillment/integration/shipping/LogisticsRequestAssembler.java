package com.dongqh.fulfillment.integration.shipping;

import com.dongqh.fulfillment.enums.FulfillmentType;
import com.dongqh.fulfillment.domain.model.FulfillmentClaim;
import com.dongqh.fulfillment.domain.model.LogisticsFulfillmentPayload;
import com.dongqh.fulfillment.integration.crypto.shipping.AddressCipher;
import com.dongqh.fulfillment.domain.shipping.ShippingDomainException;
import com.dongqh.fulfillment.domain.shipping.ShippingErrorCode;
import com.dongqh.fulfillment.domain.model.shipping.LogisticsFulfillmentRequest;
import com.dongqh.fulfillment.persistence.shipping.ShippingAddressSnapshot;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrder;
import com.dongqh.fulfillment.persistence.shipping.ShippingOrderRepository;
import com.dongqh.fulfillment.service.shipping.ShippingAddressSnapshotService;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 将物流履约快照组装成物流系统请求。
 * 该类已经是具体实现，不再保留无意义的 Impl 后缀。
 */
@Component
public class LogisticsRequestAssembler {

    private final ShippingOrderRepository orders;
    private final ShippingAddressSnapshotService snapshots;
    private final AddressCipher cipher;

    public LogisticsRequestAssembler(
            ShippingOrderRepository orders,
            ShippingAddressSnapshotService snapshots,
            AddressCipher cipher
    ) {
        this.orders = orders;
        this.snapshots = snapshots;
        this.cipher = cipher;
    }

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
