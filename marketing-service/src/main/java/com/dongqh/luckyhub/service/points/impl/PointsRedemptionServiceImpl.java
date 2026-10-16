package com.dongqh.luckyhub.service.points.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.domain.model.catalog.RedeemableSkuSnapshot;
import com.dongqh.luckyhub.enums.catalog.ProductType;
import com.dongqh.luckyhub.service.catalog.CatalogService;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.exception.ForbiddenException;
import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.domain.channel.inventory.dto.ReserveChannelStockCommand;
import com.dongqh.luckyhub.domain.channel.inventory.service.ChannelInventoryService;
import com.dongqh.luckyhub.domain.dto.points.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.domain.dto.points.PointsMutationCommand;
import com.dongqh.luckyhub.domain.dto.points.PointsRedemptionQuery;
import com.dongqh.luckyhub.domain.dto.points.PointsReversalCommand;
import com.dongqh.luckyhub.domain.dto.points.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.domain.po.points.PointsRedemptionOrder;
import com.dongqh.luckyhub.domain.po.points.PointsRedemptionOffer;
import com.dongqh.luckyhub.enums.points.PointsBusinessType;
import com.dongqh.luckyhub.enums.points.PointsErrorCode;
import com.dongqh.luckyhub.enums.points.PointsRedemptionStatus;
import com.dongqh.luckyhub.mapper.points.PointsRedemptionOrderMapper;
import com.dongqh.luckyhub.service.points.PointsAccountService;
import com.dongqh.luckyhub.service.points.PointsRedemptionService;
import com.dongqh.luckyhub.service.points.PointsRedemptionOfferService;
import com.dongqh.luckyhub.domain.vo.points.PointsRedemptionView;
import com.dongqh.luckyhub.domain.po.shipping.ShippingAddressSnapshot;
import com.dongqh.luckyhub.enums.shipping.ShippingErrorCode;
import com.dongqh.luckyhub.enums.shipping.ShippingSourceType;
import com.dongqh.luckyhub.service.shipping.ShippingAddressSnapshotService;
import com.dongqh.luckyhub.service.shipping.ShippingOrderService;
import com.dongqh.luckyhub.domain.model.shipping.CreateShippingOrderCommand;
import com.dongqh.luckyhub.domain.vo.shipping.ShippingAddressSnapshotView;
import com.dongqh.luckyhub.domain.po.shipping.ShippingOrder;
import com.dongqh.luckyhub.mapper.shipping.ShippingOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PointsRedemptionServiceImpl implements PointsRedemptionService {

    private static final String POINTS_CHANNEL = "POINTS";

    private final CatalogService catalogService;
    private final PointsAccountService accountService;
    private final ChannelInventoryService inventoryService;
    private final PointsRedemptionOrderMapper orderMapper;
    private final ShippingAddressSnapshotService snapshotService;
    private final ShippingOrderService shippingOrderService;
    private final ShippingOrderMapper shippingOrderMapper;
    private final PointsRedemptionOfferService offerService;

    @Autowired
    public PointsRedemptionServiceImpl(
            CatalogService catalogService,
            PointsAccountService accountService,
            ChannelInventoryService inventoryService,
            PointsRedemptionOrderMapper orderMapper,
            ShippingAddressSnapshotService snapshotService,
            ShippingOrderService shippingOrderService,
            ShippingOrderMapper shippingOrderMapper,
            PointsRedemptionOfferService offerService
    ) {
        this.catalogService = catalogService;
        this.accountService = accountService;
        this.inventoryService = inventoryService;
        this.orderMapper = orderMapper;
        this.snapshotService = snapshotService;
        this.shippingOrderService = shippingOrderService;
        this.shippingOrderMapper = shippingOrderMapper;
        this.offerService = offerService;
    }

    public PointsRedemptionServiceImpl(
            CatalogService catalogService,
            PointsAccountService accountService,
            ChannelInventoryService inventoryService,
            PointsRedemptionOrderMapper orderMapper,
            ShippingAddressSnapshotService snapshotService,
            ShippingOrderService shippingOrderService,
            ShippingOrderMapper shippingOrderMapper
    ) {
        this(catalogService, accountService, inventoryService, orderMapper, snapshotService,
                shippingOrderService, shippingOrderMapper, null);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointsRedemptionView create(long userId, CreatePointsRedemptionCommand command) {
        String redemptionNo = command.redemptionNo().trim();
        PointsRedemptionOrder existing = find(redemptionNo);
        if (existing != null) {
            validateIdentity(existing, userId, command.skuId(), command.offerId(),
                    command.quantity(), command.addressId());
            return view(existing);
        }

        if (command.offerId() != null) {
            return createHmallOffer(userId, command, redemptionNo);
        }

        RedeemableSkuSnapshot sku = catalogService.findRedeemableSku(command.skuId())
                .orElseThrow(() -> error(PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE));
        validateAddressShape(sku.productType(), command.addressId());
        long totalPoints;
        try {
            totalPoints = Math.multiplyExact(sku.pointsPrice(), command.quantity().longValue());
        } catch (ArithmeticException exception) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }

        PointsRedemptionOrder order = newOrder(
                redemptionNo, userId, command.quantity(), sku, totalPoints);
        if (orderMapper.claim(order) != 1) {
            PointsRedemptionOrder winner = require(redemptionNo);
            validateIdentity(winner, userId, command.skuId(), null,
                    command.quantity(), command.addressId());
            return view(winner);
        }

        if (command.addressId() != null) {
            ShippingAddressSnapshot snapshot = snapshotService.create(userId, command.addressId(),
                    ShippingSourceType.POINTS_REDEMPTION, String.valueOf(order.getId()));
            if (orderMapper.attachAddressSnapshot(order.getId(), snapshot.getId()) != 1) {
                throw shippingError(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
            }
            order.setAddressSnapshotId(snapshot.getId());
        }

        inventoryService.reserve(new ReserveChannelStockCommand(
                sku.skuId(), POINTS_CHANNEL, command.quantity(), redemptionNo));
        accountService.debit(new PointsMutationCommand(
                userId, PointsBusinessType.REDEMPTION, redemptionNo,
                totalPoints, "积分兑换商品"));
        inventoryService.confirm(redemptionNo);
        if (orderMapper.completeProcessing(redemptionNo) != 1) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
        if (order.getProductType() == ProductType.PHYSICAL) {
            var shipping = shippingOrderService.create(new CreateShippingOrderCommand(
                    ShippingSourceType.POINTS_REDEMPTION, String.valueOf(order.getId()), order.getUserId(),
                    order.getAddressSnapshotId(), order.getSkuCode(), order.getProductName(),
                    order.getImageUrl(), order.getQuantity(), null));
            if (orderMapper.attachShippingOrder(order.getId(), shipping.id()) != 1) {
                throw shippingError(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
            }
        }
        return view(require(redemptionNo));
    }

    private PointsRedemptionView createHmallOffer(
            long userId,
            CreatePointsRedemptionCommand command,
            String redemptionNo
    ) {
        PointsRedemptionOffer offer = offerService.requireAvailable(command.offerId());
        validateAddressShape(ProductType.PHYSICAL, command.addressId());
        long totalPoints;
        try {
            totalPoints = Math.multiplyExact(offer.getUnitPoints(), command.quantity().longValue());
        } catch (ArithmeticException exception) {
            throw error(PointsErrorCode.POINTS_AMOUNT_INVALID);
        }

        PointsRedemptionOrder order = newHmallOrder(
                redemptionNo, userId, command.quantity(), offer, totalPoints);
        if (orderMapper.claim(order) != 1) {
            PointsRedemptionOrder winner = require(redemptionNo);
            validateIdentity(winner, userId, null, command.offerId(),
                    command.quantity(), command.addressId());
            return view(winner);
        }

        ShippingAddressSnapshot snapshot = snapshotService.create(userId, command.addressId(),
                ShippingSourceType.POINTS_REDEMPTION, String.valueOf(order.getId()));
        if (orderMapper.attachAddressSnapshot(order.getId(), snapshot.getId()) != 1) {
            throw shippingError(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
        }
        order.setAddressSnapshotId(snapshot.getId());

        offerService.deductStock(offer.getId(), command.quantity());
        accountService.debit(new PointsMutationCommand(
                userId, PointsBusinessType.REDEMPTION, redemptionNo,
                totalPoints, "积分兑换商城商品"));
        if (orderMapper.completeProcessing(redemptionNo) != 1) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
        order.setStatus(PointsRedemptionStatus.COMPLETED);

        var shipping = shippingOrderService.create(new CreateShippingOrderCommand(
                ShippingSourceType.POINTS_REDEMPTION, String.valueOf(order.getId()), order.getUserId(),
                order.getAddressSnapshotId(), order.getHmallItemId(), order.getSkuCode(),
                order.getProductName(), order.getImageUrl(), order.getQuantity(), null));
        if (orderMapper.attachShippingOrder(order.getId(), shipping.id()) != 1) {
            throw shippingError(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
        }
        order.setShippingOrderId(shipping.id());
        return view(require(redemptionNo));
    }

    @Override
    public PointsRedemptionView get(long userId, String rawRedemptionNo) {
        PointsRedemptionOrder order = require(rawRedemptionNo.trim());
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new ForbiddenException();
        }
        return view(order);
    }

    @Override
    public PageResponse<PointsRedemptionView> page(long userId, PointsRedemptionQuery query) {
        LambdaQueryWrapper<PointsRedemptionOrder> wrapper =
                new LambdaQueryWrapper<PointsRedemptionOrder>()
                        .eq(PointsRedemptionOrder::getUserId, userId)
                        .eq(query.getStatus() != null,
                                PointsRedemptionOrder::getStatus, query.getStatus())
                        .orderByDesc(PointsRedemptionOrder::getCreatedAt)
                        .orderByDesc(PointsRedemptionOrder::getId);
        Page<PointsRedemptionOrder> result = orderMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<PointsRedemptionView> records = result.getRecords().stream().map(this::view).toList();
        return new PageResponse<>(records, result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointsRedemptionView reverse(
            String rawRedemptionNo,
            ReversePointsRedemptionCommand command
    ) {
        String redemptionNo = rawRedemptionNo.trim();
        String reversalNo = command.reversalNo().trim();
        String reason = command.reason().trim();
        PointsRedemptionOrder order = orderMapper.lockByRedemptionNo(redemptionNo);
        if (order == null) {
            throw error(PointsErrorCode.REDEMPTION_NOT_FOUND);
        }
        if (order.getStatus() == PointsRedemptionStatus.REVERSED) {
            if (!Objects.equals(order.getReversalNo(), reversalNo)) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
            return view(order);
        }
        if (order.getStatus() != PointsRedemptionStatus.COMPLETED) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
        if (order.getShippingOrderId() != null) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }

        try {
            accountService.reverseDebit(new PointsReversalCommand(
                    order.getUserId(), PointsBusinessType.REDEMPTION,
                    redemptionNo, reversalNo, reason));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == PointsErrorCode.POINTS_REVERSAL_CONFLICT) {
                throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
            }
            throw exception;
        }
        if (order.getOfferId() != null) {
            offerService.restoreStock(order.getOfferId(), order.getQuantity());
        } else {
            inventoryService.reverseConfirmed(redemptionNo);
        }
        if (orderMapper.reverseCompleted(redemptionNo, reversalNo, reason) != 1) {
            throw error(PointsErrorCode.REDEMPTION_STATE_CONFLICT);
        }
        return view(require(redemptionNo));
    }

    private PointsRedemptionOrder newOrder(
            String redemptionNo,
            long userId,
            int quantity,
            RedeemableSkuSnapshot sku,
            long totalPoints
    ) {
        PointsRedemptionOrder order = new PointsRedemptionOrder();
        order.setRedemptionNo(redemptionNo);
        order.setUserId(userId);
        order.setSkuId(sku.skuId());
        order.setQuantity(quantity);
        order.setUnitPoints(sku.pointsPrice());
        order.setTotalPoints(totalPoints);
        order.setProductCode(sku.productCode());
        order.setProductName(sku.productName());
        order.setSkuCode(sku.skuCode());
        order.setSkuName(sku.skuName());
        order.setProductType(sku.productType());
        order.setImageUrl(sku.imageUrl());
        order.setStatus(PointsRedemptionStatus.PROCESSING);
        return order;
    }

    private PointsRedemptionOrder newHmallOrder(
            String redemptionNo,
            long userId,
            int quantity,
            PointsRedemptionOffer offer,
            long totalPoints
    ) {
        PointsRedemptionOrder order = new PointsRedemptionOrder();
        order.setRedemptionNo(redemptionNo);
        order.setUserId(userId);
        order.setOfferId(offer.getId());
        order.setHmallItemId(offer.getHmallItemId());
        order.setQuantity(quantity);
        order.setUnitPoints(offer.getUnitPoints());
        order.setTotalPoints(totalPoints);
        order.setCashPriceCent(offer.getCashPriceCent());
        order.setProductCode("HMALL-ITEM-" + offer.getHmallItemId());
        order.setProductName(offer.getItemName());
        order.setSkuCode("HMALL-ITEM-" + offer.getHmallItemId());
        order.setSkuName(offer.getItemName());
        order.setProductType(ProductType.PHYSICAL);
        order.setImageUrl(offer.getImageUrl());
        order.setStatus(PointsRedemptionStatus.PROCESSING);
        return order;
    }

    private PointsRedemptionOrder find(String redemptionNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<PointsRedemptionOrder>()
                .eq(PointsRedemptionOrder::getRedemptionNo, redemptionNo));
    }

    private PointsRedemptionOrder require(String redemptionNo) {
        PointsRedemptionOrder order = find(redemptionNo);
        if (order == null) {
            throw error(PointsErrorCode.REDEMPTION_NOT_FOUND);
        }
        return order;
    }

    private void validateIdentity(
            PointsRedemptionOrder order,
            long userId,
            Long skuId,
            Long offerId,
            int quantity,
            Long addressId
    ) {
        if (!Objects.equals(order.getUserId(), userId)
                || !Objects.equals(order.getSkuId(), skuId)
                || !Objects.equals(order.getOfferId(), offerId)
                || !Objects.equals(order.getQuantity(), quantity)
                || !sameAddress(order, addressId)) {
            throw error(PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);
        }
    }

    private PointsRedemptionView view(PointsRedemptionOrder order) {
        ShippingOrder shipping = order.getShippingOrderId() == null ? null
                : shippingOrderMapper.selectById(order.getShippingOrderId());
        return new PointsRedemptionView(
                order.getId(), order.getRedemptionNo(), order.getUserId(), order.getSkuId(),
                order.getOfferId(), order.getHmallItemId(), order.getQuantity(),
                order.getUnitPoints(), order.getTotalPoints(), order.getCashPriceCent(),
                order.getProductCode(), order.getProductName(), order.getSkuCode(),
                order.getSkuName(), order.getProductType(), order.getImageUrl(),
                order.getStatus(), order.getReversalNo(), order.getFailureReason(),
                snapshotView(order.getAddressSnapshotId()), order.getShippingOrderId(),
                shipping == null ? null : shipping.getShippingNo(),
                shipping == null ? null : shipping.getStatus(),
                order.getCreatedAt(), order.getUpdatedAt());
    }

    private void validateAddressShape(ProductType productType, Long addressId) {
        if ((productType == ProductType.PHYSICAL) != (addressId != null)) {
            throw shippingError(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
    }

    private boolean sameAddress(PointsRedemptionOrder order, Long addressId) {
        if (order.getAddressSnapshotId() == null) {
            return addressId == null;
        }
        return addressId != null
                && Objects.equals(snapshotService.require(order.getAddressSnapshotId()).getAddressId(), addressId);
    }

    private ShippingAddressSnapshotView snapshotView(Long snapshotId) {
        if (snapshotId == null) {
            return null;
        }
        ShippingAddressSnapshot snapshot = snapshotService.require(snapshotId);
        return new ShippingAddressSnapshotView(snapshot.getId(), snapshot.getSnapshotNo(),
                snapshot.getReceiverMasked(), snapshot.getPhoneMasked(), snapshot.getRegionMasked());
    }

    private BusinessException error(PointsErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    private BusinessException shippingError(ShippingErrorCode errorCode) {
        return new BusinessException(errorCode);
    }
}
