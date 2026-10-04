package com.dongqh.luckyhub.points.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.hmapi.client.FulfillmentClient;
import com.dongqh.hmapi.dto.CreateFulfillmentShippingOrderRequest;
import com.dongqh.hmapi.dto.FulfillmentShippingOrderDTO;
import com.dongqh.hmapi.dto.InternalServiceErrorDTO;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.points.dto.PointsMutationCommand;
import com.dongqh.luckyhub.points.dto.PointsRedemptionQuery;
import com.dongqh.luckyhub.points.dto.PointsReversalCommand;
import com.dongqh.luckyhub.points.dto.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOffer;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOrder;
import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.enums.PointsProductType;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStatus;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStep;
import com.dongqh.luckyhub.points.exception.PointsDomainException;
import com.dongqh.luckyhub.points.mapper.PointsRedemptionOrderMapper;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.service.PointsRedemptionOfferService;
import com.dongqh.luckyhub.points.service.PointsRedemptionService;
import com.dongqh.luckyhub.points.service.PointsRedemptionStateService;
import com.dongqh.luckyhub.points.service.port.PointsInventoryPort;
import com.dongqh.luckyhub.points.service.port.PointsRedeemableSkuPort;
import com.dongqh.luckyhub.points.service.port.PointsRedeemableSkuSnapshot;
import com.dongqh.luckyhub.points.service.port.PointsUserEligibilityPort;
import com.dongqh.luckyhub.points.vo.PointsPageResponse;
import com.dongqh.luckyhub.points.vo.PointsRedemptionView;
import com.dongqh.luckyhub.points.vo.PointsShippingAddressView;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PointsRedemptionServiceImpl implements PointsRedemptionService {

    private final PointsUserEligibilityPort userEligibility;
    private final PointsRedeemableSkuPort skuCatalog;
    private final PointsInventoryPort inventory;
    private final PointsAccountService accounts;
    private final PointsRedemptionOfferService offers;
    private final PointsRedemptionStateService state;
    private final PointsRedemptionOrderMapper orders;
    private final FulfillmentClient fulfillment;

    public PointsRedemptionServiceImpl(
            PointsUserEligibilityPort userEligibility,
            PointsRedeemableSkuPort skuCatalog,
            PointsInventoryPort inventory,
            PointsAccountService accounts,
            PointsRedemptionOfferService offers,
            PointsRedemptionStateService state,
            PointsRedemptionOrderMapper orders,
            FulfillmentClient fulfillment
    ) {
        this.userEligibility = userEligibility;
        this.skuCatalog = skuCatalog;
        this.inventory = inventory;
        this.accounts = accounts;
        this.offers = offers;
        this.state = state;
        this.orders = orders;
        this.fulfillment = fulfillment;
    }

    @Override
    public PointsRedemptionView create(long userId, String authorization, CreatePointsRedemptionCommand command) {
        if (userId <= 0 || command == null || command.redemptionNo() == null
                || command.redemptionNo().isBlank() || command.redemptionNo().trim().length() > 64
                || command.quantity() == null || command.quantity() <= 0
                || ((command.skuId() == null) == (command.offerId() == null))) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
        String redemptionNo = command.redemptionNo().trim();
        String fingerprint = fingerprint(userId, command);
        PointsRedemptionOrder order = find(redemptionNo);
        if (order == null) {
            if (!userEligibility.isEnabled(userId)) {
                throw error(PointsErrorCode.POINTS_USER_UNAVAILABLE);
            }
            order = newOrder(userId, command, redemptionNo, fingerprint);
            if (orders.claim(order) != 1) {
                order = require(redemptionNo);
            }
        }
        validateIdentity(order, userId, fingerprint);
        if (order.getStatus() == PointsRedemptionStatus.COMPENSATED) {
            throw originalError(order);
        }
        if (order.getStatus() == PointsRedemptionStatus.COMPLETED) {
            return view(order);
        }

        try {
            return process(redemptionNo, authorization);
        } catch (StepExecutionFailure failure) {
            RuntimeException cause = failure.failure();
            PointsErrorCode mapped = cause instanceof PointsDomainException domain
                    ? domain.errorCode()
                    : cause instanceof FeignException remote ? mapRemoteError(remote) : null;
            if (mapped == null) {
                throw cause;
            }
            if (compensatable(mapped)) {
                PointsRedemptionView concurrentWinner = compensate(redemptionNo, failure.expectedStep(), mapped);
                if (concurrentWinner != null) {
                    return concurrentWinner;
                }
            }
            throw error(mapped);
        }
    }

    @Override
    public PointsRedemptionView get(long userId, String rawRedemptionNo) {
        PointsRedemptionOrder order = require(rawRedemptionNo == null ? "" : rawRedemptionNo.trim());
        if (!Objects.equals(order.getUserId(), userId)
                || order.getStatus() == PointsRedemptionStatus.COMPENSATED) {
            throw error(PointsErrorCode.REDEMPTION_NOT_FOUND);
        }
        return view(order);
    }

    @Override
    public PointsPageResponse<PointsRedemptionView> page(long userId, PointsRedemptionQuery query) {
        if (query.getStatus() == PointsRedemptionStatus.COMPENSATED) {
            return new PointsPageResponse<>(List.of(), 0, query.getPage(), query.getSize(), 0);
        }
        LambdaQueryWrapper<PointsRedemptionOrder> wrapper = new LambdaQueryWrapper<PointsRedemptionOrder>()
                .eq(PointsRedemptionOrder::getUserId, userId)
                .ne(PointsRedemptionOrder::getStatus, PointsRedemptionStatus.COMPENSATED)
                .eq(query.getStatus() != null, PointsRedemptionOrder::getStatus, query.getStatus())
                .orderByDesc(PointsRedemptionOrder::getCreatedAt)
                .orderByDesc(PointsRedemptionOrder::getId);
        Page<PointsRedemptionOrder> result = orders.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        Map<Long, FulfillmentShippingOrderDTO> shipping = shippingByIds(result.getRecords());
        List<PointsRedemptionView> records = result.getRecords().stream()
                .map(order -> view(order, shipping.get(order.getShippingOrderId()))).toList();
        return new PointsPageResponse<>(records, result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages());
    }

    @Override
    public PointsRedemptionView reverse(String rawRedemptionNo, ReversePointsRedemptionCommand command) {
        if (rawRedemptionNo == null || rawRedemptionNo.isBlank() || command == null
                || command.reversalNo() == null || command.reversalNo().isBlank()
                || command.reason() == null || command.reason().isBlank()) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
        String redemptionNo = rawRedemptionNo.trim();
        String reversalNo = command.reversalNo().trim();
        String reason = command.reason().trim();
        PointsRedemptionOrder order = state.withLockedOrder(redemptionNo, current -> {
            if (current.getStatus() == PointsRedemptionStatus.REVERSED) {
                if (!Objects.equals(current.getReversalNo(), reversalNo)) {
                    throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
                }
                return current;
            }
            if (current.getStatus() != PointsRedemptionStatus.COMPLETED || current.getShippingOrderId() != null
                    || (current.getReversalNo() != null && !Objects.equals(current.getReversalNo(), reversalNo))) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
            if (current.getOfferId() != null) {
                if (orders.claimReversalNo(redemptionNo, reversalNo) != 1) {
                    throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
                }
                try {
                    accounts.reverseDebit(new PointsReversalCommand(current.getUserId(),
                            PointsBusinessType.REDEMPTION, redemptionNo, reversalNo, bounded(reason)));
                } catch (PointsDomainException exception) {
                    if (exception.errorCode() == PointsErrorCode.POINTS_REVERSAL_CONFLICT) {
                        throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
                    }
                    throw exception;
                }
                offers.restoreStock(current.getOfferId(), current.getQuantity());
                if (orders.reverseCompleted(redemptionNo, reversalNo, bounded(reason)) != 1) {
                    throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
                }
                return require(redemptionNo);
            }
            if (orders.claimReversalNo(redemptionNo, reversalNo) != 1) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
            return current;
        });
        if (order.getStatus() == PointsRedemptionStatus.REVERSED) {
            return view(order);
        }

        try {
            accounts.reverseDebit(new PointsReversalCommand(order.getUserId(), PointsBusinessType.REDEMPTION,
                    redemptionNo, reversalNo, bounded(reason)));
        } catch (PointsDomainException exception) {
            if (exception.errorCode() == PointsErrorCode.POINTS_REVERSAL_CONFLICT) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
            throw exception;
        }
        inventory.reverseConfirmed(redemptionNo);
        if (orders.reverseCompleted(redemptionNo, reversalNo, bounded(reason)) != 1) {
            PointsRedemptionOrder winner = require(redemptionNo);
            if (winner.getStatus() != PointsRedemptionStatus.REVERSED
                    || !Objects.equals(winner.getReversalNo(), reversalNo)) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
        }
        return view(require(redemptionNo));
    }

    private PointsRedemptionView process(String redemptionNo, String authorization) {
        while (true) {
            PointsRedemptionOrder order = require(redemptionNo);
            if (order.getStatus() == PointsRedemptionStatus.COMPLETED) {
                return view(order);
            }
            if (order.getStatus() == PointsRedemptionStatus.COMPENSATED) {
                throw originalError(order);
            }
            PointsRedemptionStep step = order.getProcessStep();
            switch (step) {
                case CLAIMED -> {
                    withStepLock(redemptionNo, current -> {
                        if (!processingAt(current, PointsRedemptionStep.CLAIMED)) return null;
                        if (current.getSkuId() == null) {
                            state.reserveOfferStock(redemptionNo, current.getOfferId(), current.getQuantity());
                        } else {
                            inventory.reserve(current.getSkuId(), current.getQuantity(), current.getRedemptionNo());
                            advanceLocked(current, PointsRedemptionStep.SKU_RESERVED);
                        }
                        return null;
                    });
                }
                case SKU_RESERVED, OFFER_RESERVED -> {
                    withStepLock(redemptionNo, current -> {
                        if (current.getStatus() != PointsRedemptionStatus.PROCESSING
                                || (current.getProcessStep() != PointsRedemptionStep.SKU_RESERVED
                                && current.getProcessStep() != PointsRedemptionStep.OFFER_RESERVED)) {
                            return null;
                        }
                        accounts.debit(new PointsMutationCommand(current.getUserId(), PointsBusinessType.REDEMPTION,
                                redemptionNo, current.getTotalPoints(), current.getOfferId() == null
                                ? "积分兑换商品" : "积分兑换商城商品"));
                        advanceLocked(current, PointsRedemptionStep.POINTS_DEBITED);
                        return null;
                    });
                }
                case POINTS_DEBITED -> {
                    withStepLock(redemptionNo, current -> {
                        if (!processingAt(current, PointsRedemptionStep.POINTS_DEBITED)) return null;
                        if (current.getSkuId() != null) {
                            inventory.confirm(redemptionNo);
                        }
                        advanceLocked(current, PointsRedemptionStep.SKU_CONFIRMED);
                        return null;
                    });
                }
                case SKU_CONFIRMED -> {
                    withStepLock(redemptionNo, current -> {
                        if (!processingAt(current, PointsRedemptionStep.SKU_CONFIRMED)) return null;
                        if (current.getProductType() != PointsProductType.PHYSICAL) {
                            completeLocked(current, null);
                            return null;
                        }
                        if (authorization == null || authorization.isBlank()) {
                            throw error(PointsErrorCode.REDEMPTION_ADDRESS_INVALID);
                        }
                        FulfillmentShippingOrderDTO accepted = fulfillment.createShippingOrder(authorization,
                                new CreateFulfillmentShippingOrderRequest("POINTS_REDEMPTION",
                                        String.valueOf(current.getId()), current.getUserId(), current.getAddressId(),
                                        current.getHmallItemId(), current.getSkuCode(), current.getProductName(),
                                        current.getImageUrl(), current.getQuantity()));
                        if (accepted == null || accepted.id() == null) {
                            throw new IllegalStateException("Fulfillment未返回已持久化的发货单");
                        }
                        if (orders.attachShippingOrder(redemptionNo, accepted.id()) != 1) {
                            PointsRedemptionOrder winner = require(redemptionNo);
                            if (!Objects.equals(winner.getShippingOrderId(), accepted.id())
                                    || winner.getProcessStep() != PointsRedemptionStep.FULFILLMENT_ACCEPTED) {
                                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
                            }
                        }
                        return null;
                    });
                }
                case FULFILLMENT_ACCEPTED -> complete(redemptionNo, order.getShippingOrderId());
                case COMPENSATING_POINTS, COMPENSATING_SKU, COMPENSATING_OFFER -> {
                    resumeCompensation(redemptionNo);
                }
                case COMPENSATED -> throw originalError(order);
                case COMPLETED -> {
                    return view(order);
                }
            }
        }
    }

    private PointsRedemptionView compensate(
            String redemptionNo,
            PointsRedemptionStep expectedStep,
            PointsErrorCode failure
    ) {
        boolean resume = state.withLockedOrder(redemptionNo, order -> {
            if (order.getStatus() == PointsRedemptionStatus.COMPLETED
                    || order.getStatus() == PointsRedemptionStatus.REVERSED
                    || order.getStatus() == PointsRedemptionStatus.COMPENSATED) {
                return false;
            }
            if (order.getStatus() != PointsRedemptionStatus.PROCESSING) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
            PointsRedemptionStep current = order.getProcessStep();
            if (current == PointsRedemptionStep.COMPENSATING_POINTS
                    || current == PointsRedemptionStep.COMPENSATING_SKU
                    || current == PointsRedemptionStep.COMPENSATING_OFFER) {
                return true;
            }
            if (current != expectedStep) {
                return false;
            }
            if (current == PointsRedemptionStep.FULFILLMENT_ACCEPTED
                    || current == PointsRedemptionStep.COMPLETED) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
            if (current == PointsRedemptionStep.CLAIMED) {
                if (orders.compensate(redemptionNo, current.name(), failure.name(),
                        bounded(failure.message())) != 1) {
                    throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
                }
                return false;
            }
            PointsRedemptionStep compensation = current == PointsRedemptionStep.POINTS_DEBITED
                    || current == PointsRedemptionStep.SKU_CONFIRMED
                    ? PointsRedemptionStep.COMPENSATING_POINTS
                    : order.getSkuId() == null
                    ? PointsRedemptionStep.COMPENSATING_OFFER : PointsRedemptionStep.COMPENSATING_SKU;
            if (orders.setCompensation(redemptionNo, current.name(), compensation.name(), failure.name(),
                    bounded(failure.message())) != 1) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
            return true;
        });
        PointsRedemptionOrder current = require(redemptionNo);
        if (current.getStatus() == PointsRedemptionStatus.COMPLETED
                || current.getStatus() == PointsRedemptionStatus.REVERSED) {
            return view(current);
        }
        if (resume) {
            resumeCompensation(redemptionNo);
        }
        return null;
    }

    private void resumeCompensation(String redemptionNo) {
        while (true) {
            PointsRedemptionOrder order = require(redemptionNo);
            if (order.getStatus() == PointsRedemptionStatus.COMPENSATED) {
                return;
            }
            switch (order.getProcessStep()) {
                case COMPENSATING_POINTS -> {
                    state.withLockedOrder(redemptionNo, current -> {
                        if (!processingAt(current, PointsRedemptionStep.COMPENSATING_POINTS)) return null;
                        accounts.reverseDebit(new PointsReversalCommand(current.getUserId(),
                                PointsBusinessType.REDEMPTION, redemptionNo, redemptionNo + ":COMPENSATE",
                                current.getFailureReason()));
                        PointsRedemptionStep next = current.getSkuId() == null
                                ? PointsRedemptionStep.COMPENSATING_OFFER : PointsRedemptionStep.COMPENSATING_SKU;
                        advanceLocked(current, next);
                        return null;
                    });
                }
                case COMPENSATING_SKU -> {
                    state.withLockedOrder(redemptionNo, current -> {
                        if (!processingAt(current, PointsRedemptionStep.COMPENSATING_SKU)) return null;
                        inventory.compensate(redemptionNo);
                        if (orders.compensate(redemptionNo, PointsRedemptionStep.COMPENSATING_SKU.name(),
                                current.getOriginalErrorCode(), current.getFailureReason()) != 1) {
                            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
                        }
                        return null;
                    });
                }
                case COMPENSATING_OFFER -> {
                    state.compensateOfferStock(redemptionNo);
                    return;
                }
                case COMPENSATED -> {
                    return;
                }
                default -> throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
        }
    }

    private void complete(String redemptionNo, Long shippingOrderId) {
        state.withLockedOrder(redemptionNo, order -> {
            completeLocked(order, shippingOrderId);
            return null;
        });
    }

    private void completeLocked(PointsRedemptionOrder order, Long shippingOrderId) {
        if (order.getStatus() == PointsRedemptionStatus.COMPLETED
                || order.getStatus() == PointsRedemptionStatus.REVERSED) {
            if (!Objects.equals(order.getShippingOrderId(), shippingOrderId)) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
            return;
        }
        PointsRedemptionStep expectedStep = shippingOrderId == null
                ? PointsRedemptionStep.SKU_CONFIRMED : PointsRedemptionStep.FULFILLMENT_ACCEPTED;
        if (!processingAt(order, expectedStep)
                || orders.complete(order.getRedemptionNo(), expectedStep.name(), shippingOrderId) != 1) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
    }

    private boolean processingAt(PointsRedemptionOrder order, PointsRedemptionStep step) {
        return order.getStatus() == PointsRedemptionStatus.PROCESSING && order.getProcessStep() == step;
    }

    private void advanceLocked(PointsRedemptionOrder order, PointsRedemptionStep target) {
        if (!processingAt(order, order.getProcessStep())
                || orders.advanceStep(order.getRedemptionNo(), order.getProcessStep().name(), target.name()) != 1) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
    }

    private <T> T withStepLock(String redemptionNo, Function<PointsRedemptionOrder, T> action) {
        AtomicReference<PointsRedemptionStep> expectedStep = new AtomicReference<>();
        try {
            return state.withLockedOrder(redemptionNo, order -> {
                expectedStep.set(order.getProcessStep());
                return action.apply(order);
            });
        } catch (RuntimeException failure) {
            PointsRedemptionStep step = expectedStep.get();
            if (step == null) {
                throw failure;
            }
            throw new StepExecutionFailure(step, failure);
        }
    }

    private PointsRedemptionOrder newOrder(
            long userId,
            CreatePointsRedemptionCommand command,
            String redemptionNo,
            String fingerprint
    ) {
        PointsRedemptionOrder order = new PointsRedemptionOrder();
        order.setRedemptionNo(redemptionNo);
        order.setUserId(userId);
        order.setQuantity(command.quantity());
        order.setAddressId(command.addressId());
        order.setRequestFingerprint(fingerprint);
        order.setStatus(PointsRedemptionStatus.PROCESSING);
        order.setProcessStep(PointsRedemptionStep.CLAIMED);

        if (command.offerId() != null) {
            PointsRedemptionOffer offer = offers.requireAvailable(command.offerId());
            order.setOfferId(offer.getId());
            order.setHmallItemId(offer.getHmallItemId());
            order.setUnitPoints(offer.getUnitPoints());
            order.setCashPriceCent(offer.getCashPriceCent());
            order.setProductCode("HMALL-ITEM-" + offer.getHmallItemId());
            order.setProductName(offer.getItemName());
            order.setSkuCode("HMALL-ITEM-" + offer.getHmallItemId());
            order.setSkuName(offer.getItemName());
            order.setProductType(PointsProductType.PHYSICAL);
            order.setImageUrl(offer.getImageUrl());
        } else {
            PointsRedeemableSkuSnapshot sku = skuCatalog.getRedeemableSku(command.skuId())
                    .orElseThrow(() -> error(PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE));
            order.setSkuId(sku.skuId());
            order.setUnitPoints(sku.pointsPrice());
            order.setProductCode(sku.productCode());
            order.setProductName(sku.productName());
            order.setSkuCode(sku.skuCode());
            order.setSkuName(sku.skuName());
            try {
                order.setProductType(PointsProductType.valueOf(sku.productType()));
            } catch (RuntimeException exception) {
                throw error(PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE);
            }
            order.setImageUrl(sku.imageUrl());
        }
        validateAddressShape(order.getProductType(), command.addressId());
        try {
            order.setTotalPoints(Math.multiplyExact(order.getUnitPoints(), command.quantity().longValue()));
        } catch (ArithmeticException exception) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }
        return order;
    }

    private void validateAddressShape(PointsProductType productType, Long addressId) {
        if ((productType == PointsProductType.PHYSICAL) != (addressId != null)) {
            throw error(PointsErrorCode.REDEMPTION_ADDRESS_INVALID);
        }
    }

    private void validateIdentity(PointsRedemptionOrder order, long userId, String fingerprint) {
        if (!Objects.equals(order.getUserId(), userId)
                || !Objects.equals(order.getRequestFingerprint(), fingerprint)) {
            throw error(PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);
        }
    }

    private String fingerprint(long userId, CreatePointsRedemptionCommand command) {
        String value = userId + "|" + command.skuId() + "|" + command.offerId() + "|"
                + command.quantity() + "|" + command.addressId();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private PointsRedemptionView view(PointsRedemptionOrder order) {
        return view(order, shippingByIds(List.of(order)).get(order.getShippingOrderId()));
    }

    private PointsRedemptionView view(PointsRedemptionOrder order, FulfillmentShippingOrderDTO shipping) {
        PointsShippingAddressView address = shipping == null || shipping.addressSnapshotId() == null
                ? null : new PointsShippingAddressView(shipping.addressSnapshotId(), shipping.addressSnapshotNo(),
                shipping.receiverMasked(), shipping.phoneMasked(), shipping.regionMasked());
        return new PointsRedemptionView(order.getId(), order.getRedemptionNo(), order.getUserId(), order.getSkuId(),
                order.getOfferId(), order.getHmallItemId(), order.getQuantity(), order.getUnitPoints(),
                order.getTotalPoints(), order.getCashPriceCent(), order.getProductCode(), order.getProductName(),
                order.getSkuCode(), order.getSkuName(), order.getProductType(), order.getImageUrl(),
                order.getStatus(), order.getReversalNo(), order.getFailureReason(), address,
                order.getShippingOrderId(), shipping == null ? null : shipping.shippingNo(),
                shipping == null ? null : shipping.status(), order.getCreatedAt(), order.getUpdatedAt());
    }

    private Map<Long, FulfillmentShippingOrderDTO> shippingByIds(List<PointsRedemptionOrder> records) {
        List<Long> ids = records.stream().map(PointsRedemptionOrder::getShippingOrderId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return fulfillment.getShippingOrdersByIds(ids).stream()
                    .filter(dto -> dto != null && dto.id() != null)
                    .collect(Collectors.toMap(FulfillmentShippingOrderDTO::id, dto -> dto, (left, right) -> left));
        } catch (RuntimeException unavailable) {
            return Map.of();
        }
    }

    private PointsRedemptionOrder find(String redemptionNo) {
        return orders.selectOne(new LambdaQueryWrapper<PointsRedemptionOrder>()
                .eq(PointsRedemptionOrder::getRedemptionNo, redemptionNo));
    }

    private PointsRedemptionOrder require(String redemptionNo) {
        PointsRedemptionOrder order = find(redemptionNo);
        if (order == null) {
            throw error(PointsErrorCode.REDEMPTION_NOT_FOUND);
        }
        return order;
    }

    private PointsErrorCode mapRemoteError(FeignException exception) {
        if (exception.status() < 100 || exception.status() >= 500) {
            return null;
        }
        String code = null;
        try {
            code = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(exception.contentUTF8(), InternalServiceErrorDTO.class).code();
        } catch (Exception ignored) {
            // Status fallback below keeps unknown internal responses fail-closed.
        }
        if ("INVENTORY_INSUFFICIENT".equals(code) || "INVENTORY_NOT_FOUND".equals(code)) {
            return PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE;
        }
        if ("ADDRESS_NOT_FOUND".equals(code) || "ADDRESS_INVALID".equals(code)
                || "SHIPPING_REQUEST_INVALID".equals(code)) {
            return PointsErrorCode.REDEMPTION_ADDRESS_INVALID;
        }
        if (code != null) {
            return PointsErrorCode.REDEMPTION_STATE_CONFLICT;
        }
        return PointsErrorCode.REDEMPTION_STATE_CONFLICT;
    }

    private boolean compensatable(PointsErrorCode errorCode) {
        return errorCode != PointsErrorCode.REDEMPTION_STATE_CONFLICT
                && errorCode != PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT;
    }

    private PointsDomainException originalError(PointsRedemptionOrder order) {
        try {
            return error(PointsErrorCode.valueOf(order.getOriginalErrorCode()));
        } catch (RuntimeException ignored) {
            return error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
    }

    private String bounded(String value) {
        if (value == null) {
            return "兑换补偿";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private PointsDomainException error(PointsErrorCode code) {
        return new PointsDomainException(code);
    }

    private static final class StepExecutionFailure extends RuntimeException {
        private final PointsRedemptionStep expectedStep;
        private final RuntimeException failure;

        private StepExecutionFailure(PointsRedemptionStep expectedStep, RuntimeException failure) {
            super(failure);
            this.expectedStep = expectedStep;
            this.failure = failure;
        }

        private PointsRedemptionStep expectedStep() {
            return expectedStep;
        }

        private RuntimeException failure() {
            return failure;
        }
    }
}
