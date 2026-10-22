package com.dongqh.fulfillmentservice.service.shipping;

import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.domain.model.CreateFulfillmentTaskCommand;
import com.dongqh.fulfillmentservice.domain.model.LogisticsFulfillmentPayload;
import com.dongqh.fulfillmentservice.domain.shipping.ShippingDomainException;
import com.dongqh.fulfillmentservice.domain.shipping.ShippingErrorCode;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingSourceType;
import com.dongqh.fulfillmentservice.enums.shipping.ShippingStatus;
import com.dongqh.fulfillmentservice.domain.model.shipping.CreateShippingOrderCommand;
import com.dongqh.fulfillmentservice.domain.model.shipping.CreateShippingOrderFromAddressCommand;
import com.dongqh.fulfillmentservice.domain.model.shipping.CreateShippingOrderFromSnapshotCommand;
import com.dongqh.fulfillmentservice.domain.model.shipping.ShippingAddressDetails;
import com.dongqh.fulfillmentservice.domain.model.shipping.ShippingOrderView;
import com.dongqh.fulfillmentservice.persistence.shipping.DuplicateShippingOrderException;
import com.dongqh.fulfillmentservice.persistence.shipping.ShippingAddressSnapshot;
import com.dongqh.fulfillmentservice.persistence.shipping.ShippingOrder;
import com.dongqh.fulfillmentservice.persistence.shipping.ShippingOrderRepository;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskCreationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class ShippingOrderService {

    private static final int MAX_ATTEMPTS = 5;
    private final ShippingOrderRepository orders;
    private final ShippingAddressSnapshotService snapshots;
    private final FulfillmentTaskCreationService tasks;

    public ShippingOrderService(
            ShippingOrderRepository orders,
            ShippingAddressSnapshotService snapshots,
            FulfillmentTaskCreationService tasks
    ) {
        this.orders = orders;
        this.snapshots = snapshots;
        this.tasks = tasks;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderView create(CreateShippingOrderCommand command) {
        ShippingAddressSnapshot snapshot = snapshots.require(command.addressSnapshotId());
        validateSnapshot(command, snapshot);
        var existing = orders.findBySource(command.sourceType(), command.sourceId());
        if (existing.isPresent()) {
            return view(requireEqual(existing.get(), command), snapshot);
        }

        ShippingOrder order;
        try {
            order = orders.insert(candidate(command));
        } catch (DuplicateShippingOrderException race) {
            ShippingOrder winner = findWinner(command);
            if (winner == null) {
                throw race;
            }
            return view(requireEqual(winner, command), snapshots.require(winner.addressSnapshotId()));
        }

        String fulfillmentNo = "LOGISTICS-" + order.id();
        if (!orders.markFulfilling(order.id(), fulfillmentNo)) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        tasks.create(new CreateFulfillmentTaskCommand(fulfillmentNo, command.sourceType().name(), command.sourceId(),
                FulfillmentType.LOGISTICS, command.targetUserId(),
                new LogisticsFulfillmentPayload(order.id(), command.skuCode(), command.quantity(),
                        snapshot.receiverMasked(), snapshot.phoneMasked(),
                        LogisticsFulfillmentPayload.REDACTED_REGION),
                MAX_ATTEMPTS));

        ShippingOrder persisted = orders.findById(order.id()).orElseThrow(() -> error(ShippingErrorCode.SHIPPING_NOT_FOUND));
        return view(persisted, snapshot);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderView createFromAddress(CreateShippingOrderFromAddressCommand command) {
        if (command == null || command.sourceType() == null || command.targetUserId() <= 0
                || command.addressId() <= 0 || command.sourceId() == null
                || command.sourceId().isBlank() || command.sourceId().trim().length() > 100) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        String sourceId = command.sourceId().trim();
        ShippingOrder existing = orders.findBySource(command.sourceType(), sourceId).orElse(null);
        if (existing != null) {
            ShippingAddressSnapshot originalSnapshot = snapshots.require(existing.addressSnapshotId());
            CreateShippingOrderCommand retry = new CreateShippingOrderCommand(command.sourceType(), sourceId,
                    command.targetUserId(), existing.addressSnapshotId(), command.hmallItemId(), command.skuCode(),
                    command.productName(), command.imageUrl(), command.quantity(), command.claimRequestId());
            if (!Objects.equals(originalSnapshot.addressId(), command.addressId())
                    || !Objects.equals(originalSnapshot.userId(), command.targetUserId())) {
                throw error(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
            }
            return view(requireEqual(existing, retry), originalSnapshot);
        }

        ShippingAddressSnapshot snapshot = snapshots.create(command.targetUserId(), command.addressId(),
                command.sourceType(), sourceId);
        return create(new CreateShippingOrderCommand(command.sourceType(), sourceId,
                command.targetUserId(), snapshot.id(), command.hmallItemId(), command.skuCode(),
                command.productName(), command.imageUrl(), command.quantity(), command.claimRequestId()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderView createFromFrozenAddress(CreateShippingOrderFromSnapshotCommand command,
                                                     ShippingAddressDetails address) {
        if (command == null || command.targetUserId() <= 0 || command.sourceId() == null
                || command.sourceId().isBlank() || command.sourceId().trim().length() > 100) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        String sourceId = command.sourceId().trim();
        ShippingAddressSnapshot snapshot = snapshots.createFromFrozenDetails(command.targetUserId(),
                ShippingSourceType.CASH_ORDER, sourceId, address);
        return create(new CreateShippingOrderCommand(ShippingSourceType.CASH_ORDER, sourceId,
                command.targetUserId(), snapshot.id(), null, command.skuCode(), command.productName(),
                command.imageUrl(), command.quantity(), null));
    }

    @Transactional(readOnly = true)
    public List<ShippingOrderView> getByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> normalized = ids.stream().distinct().toList();
        if (normalized.size() > 100 || normalized.stream().anyMatch(id -> id == null || id <= 0)) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        List<ShippingOrder> found = orders.findByIds(normalized);
        var snapshotsById = snapshots.requireByIds(found.stream()
                        .map(ShippingOrder::addressSnapshotId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(
                        ShippingAddressSnapshot::id, snapshot -> snapshot));
        return found.stream().map(order -> {
            ShippingAddressSnapshot snapshot = snapshotsById.get(order.addressSnapshotId());
            if (snapshot == null) {
                throw error(ShippingErrorCode.SHIPPING_NOT_FOUND);
            }
            return view(order, snapshot);
        }).toList();
    }

    @Transactional(readOnly = true)
    public ShippingOrderView getForUser(long userId, String shippingNo) {
        String requiredNo = shippingNo == null ? "" : shippingNo.trim();
        if (userId <= 0 || requiredNo.isEmpty() || requiredNo.length() > 64) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        ShippingOrder order = orders.findByShippingNo(requiredNo).orElse(null);
        if (order == null || !Objects.equals(order.targetUserId(), userId)) {
            throw error(ShippingErrorCode.SHIPPING_NOT_FOUND);
        }
        ShippingAddressSnapshot snapshot = snapshots.require(order.addressSnapshotId());
        if (!Objects.equals(snapshot.userId(), userId)) {
            throw error(ShippingErrorCode.SHIPPING_NOT_FOUND);
        }
        return view(order, snapshot);
    }

    @Transactional(readOnly = true)
    public ShippingOrderView getForUserBySource(long userId, ShippingSourceType sourceType, String sourceId) {
        String requiredSourceId = sourceId == null ? "" : sourceId.trim();
        if (userId <= 0 || sourceType == null || requiredSourceId.isEmpty() || requiredSourceId.length() > 100) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        ShippingOrder order = orders.findBySource(sourceType, requiredSourceId).orElse(null);
        if (order == null || !Objects.equals(order.targetUserId(), userId)) {
            throw error(ShippingErrorCode.SHIPPING_NOT_FOUND);
        }
        ShippingAddressSnapshot snapshot;
        try {
            snapshot = snapshots.require(order.addressSnapshotId());
        } catch (ShippingDomainException missingSnapshot) {
            if (missingSnapshot.errorCode() == ShippingErrorCode.SHIPPING_NOT_FOUND) {
                throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
            }
            throw missingSnapshot;
        }
        if (!Objects.equals(snapshot.userId(), userId)) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        return view(order, snapshot);
    }

    private ShippingOrder candidate(CreateShippingOrderCommand command) {
        return new ShippingOrder(null, "SHIPPING-" + UUID.randomUUID(), command.sourceType(), command.sourceId(),
                command.targetUserId(), command.addressSnapshotId(), command.hmallItemId(), command.skuCode(),
                command.productName(), command.imageUrl(), command.quantity(), null, command.claimRequestId(),
                null, null, null, ShippingStatus.READY, null, null, null, null, null, null, 0, null, null);
    }

    private ShippingOrder findWinner(CreateShippingOrderCommand command) {
        ShippingOrder winner = orders.findBySource(command.sourceType(), command.sourceId()).orElse(null);
        if (winner == null && command.claimRequestId() != null) {
            winner = orders.findByClaimRequestId(command.claimRequestId()).orElse(null);
        }
        return winner;
    }

    private void validateSnapshot(CreateShippingOrderCommand command, ShippingAddressSnapshot snapshot) {
        if (!Objects.equals(snapshot.userId(), command.targetUserId())
                || snapshot.sourceType() != command.sourceType()
                || !Objects.equals(snapshot.sourceId(), command.sourceId())) {
            throw error(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
        }
    }

    private ShippingOrder requireEqual(ShippingOrder order, CreateShippingOrderCommand command) {
        if (order.sourceType() != command.sourceType()
                || !Objects.equals(order.sourceId(), command.sourceId())
                || !Objects.equals(order.targetUserId(), command.targetUserId())
                || !Objects.equals(order.addressSnapshotId(), command.addressSnapshotId())
                || !Objects.equals(order.hmallItemId(), command.hmallItemId())
                || !Objects.equals(order.skuCode(), command.skuCode())
                || !Objects.equals(order.productName(), command.productName())
                || !Objects.equals(order.imageUrl(), command.imageUrl())
                || !Objects.equals(order.quantity(), command.quantity())
                || !Objects.equals(order.claimRequestId(), command.claimRequestId())) {
            throw error(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
        }
        return order;
    }

    private ShippingOrderView view(ShippingOrder order, ShippingAddressSnapshot snapshot) {
        return new ShippingOrderView(order.id(), order.shippingNo(), order.sourceType(), order.sourceId(),
                order.targetUserId(), order.addressSnapshotId(), snapshot.snapshotNo(), order.hmallItemId(),
                order.skuCode(),
                order.productName(), order.imageUrl(), order.quantity(), order.fulfillmentNo(), order.carrierCode(),
                order.carrierName(), order.waybillNo(), order.status(), order.lastErrorCode(), order.lastErrorMessage(),
                order.shippedAt(), order.deliveredAt(), order.failedAt(), order.terminatedAt(), order.createdAt(),
                order.updatedAt(), snapshot.receiverMasked(), snapshot.phoneMasked(), snapshot.regionMasked());
    }

    private ShippingDomainException error(ShippingErrorCode code) {
        return new ShippingDomainException(code);
    }
}
