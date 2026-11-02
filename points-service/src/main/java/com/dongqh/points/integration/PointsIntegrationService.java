package com.dongqh.points.integration;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.client.UserClient;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.ItemStockReservationDTO;
import com.dongqh.hmapi.dto.RedeemableSkuDTO;
import com.dongqh.hmapi.dto.ReservePointsStockRequest;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The points service's small set of remote calls.
 *
 * The business services depend on this concrete integration boundary instead of
 * four one-method ports and four one-class adapters. Remote DTO mapping and
 * inventory response validation stay in one place.
 */
@Component
public class PointsIntegrationService {

    private final ItemClient items;
    private final UserClient users;

    public PointsIntegrationService(ItemClient items, UserClient users) {
        this.items = items;
        this.users = users;
    }

    public boolean isEnabledUser(long userId) {
        return Boolean.TRUE.equals(users.isEnabled(userId));
    }

    public Optional<ItemSnapshot> getItem(long itemId) {
        List<ItemDTO> response = items.queryItemByIds(List.of(itemId));
        if (response == null || response.size() != 1) {
            return Optional.empty();
        }
        ItemDTO item = response.get(0);
        if (item == null || !Objects.equals(item.getId(), itemId)) {
            return Optional.empty();
        }
        return Optional.of(new ItemSnapshot(item.getId(), item.getName(), item.getPrice(),
                item.getStock(), item.getImage(), item.getStatus()));
    }

    public Optional<RedeemableSkuSnapshot> getRedeemableSku(long skuId) {
        RedeemableSkuDTO sku = items.getRedeemableSku(skuId);
        if (sku == null || !Objects.equals(sku.skuId(), skuId)) {
            return Optional.empty();
        }
        return Optional.of(new RedeemableSkuSnapshot(sku.skuId(), sku.productCode(), sku.productName(),
                sku.skuCode(), sku.skuName(), sku.productType(), sku.imageUrl(), sku.pointsPrice()));
    }

    public void reserve(long skuId, int quantity, String reservationNo) {
        ItemStockReservationDTO result = items.reservePointsStock(
                new ReservePointsStockRequest(skuId, quantity, reservationNo));
        requireIdentity(result, skuId, quantity, reservationNo);
    }

    public void confirm(String reservationNo) {
        requireState(items.confirmPointsStock(reservationNo), reservationNo, "CONFIRMED");
    }

    public void release(String reservationNo) {
        requireState(items.releasePointsStock(reservationNo), reservationNo, "RELEASED");
    }

    public void reverseConfirmed(String reservationNo) {
        requireState(items.reversePointsStock(reservationNo), reservationNo, "REVERSED");
    }

    public void compensate(String reservationNo) {
        ItemStockReservationDTO current;
        try {
            current = items.getPointsStockReservation(reservationNo);
        } catch (FeignException.NotFound missing) {
            return;
        }
        if (current == null || !Objects.equals(current.reservationNo(), reservationNo)) {
            throw new IllegalStateException("Item服务返回的积分库存预占与请求不一致");
        }
        switch (current.status()) {
            case "RESERVED" -> release(reservationNo);
            case "CONFIRMED" -> reverseConfirmed(reservationNo);
            case "RELEASED", "REVERSED" -> { }
            default -> throw new IllegalStateException("Item服务返回未知的积分库存状态");
        }
    }

    private void requireIdentity(ItemStockReservationDTO result, long skuId, int quantity, String reservationNo) {
        if (result == null || !Objects.equals(result.skuId(), skuId)
                || !Objects.equals(result.quantity(), quantity)
                || !Objects.equals(result.reservationNo(), reservationNo)
                || !"POINTS".equals(result.channelCode())) {
            throw new IllegalStateException("Item服务返回的积分库存预占与请求不一致");
        }
    }

    private void requireState(ItemStockReservationDTO result, String reservationNo, String state) {
        if (result == null || !Objects.equals(result.reservationNo(), reservationNo)
                || !Objects.equals(result.status(), state)) {
            throw new IllegalStateException("Item服务未确认积分库存状态变更");
        }
    }

    public record ItemSnapshot(Long id, String name, Integer priceCent, Integer stock,
                               String imageUrl, Integer status) {
    }

    public record RedeemableSkuSnapshot(Long skuId, String productCode, String productName,
                                        String skuCode, String skuName, String productType,
                                        String imageUrl, Long pointsPrice) {
    }
}
