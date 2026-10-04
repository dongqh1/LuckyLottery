package com.dongqh.pointsservice.integration;

import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.dto.ItemStockReservationDTO;
import com.dongqh.hmapi.dto.ReservePointsStockRequest;
import com.dongqh.luckyhub.points.service.port.PointsInventoryPort;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ItemClientPointsInventoryAdapter implements PointsInventoryPort {

    private final ItemClient itemClient;

    public ItemClientPointsInventoryAdapter(ItemClient itemClient) {
        this.itemClient = itemClient;
    }

    @Override
    public void reserve(long skuId, int quantity, String reservationNo) {
        ItemStockReservationDTO result = itemClient.reservePointsStock(
                new ReservePointsStockRequest(skuId, quantity, reservationNo));
        requireIdentity(result, skuId, quantity, reservationNo);
    }

    @Override
    public void confirm(String reservationNo) {
        requireState(itemClient.confirmPointsStock(reservationNo), reservationNo, "CONFIRMED");
    }

    @Override
    public void release(String reservationNo) {
        requireState(itemClient.releasePointsStock(reservationNo), reservationNo, "RELEASED");
    }

    @Override
    public void reverseConfirmed(String reservationNo) {
        requireState(itemClient.reversePointsStock(reservationNo), reservationNo, "REVERSED");
    }

    @Override
    public void compensate(String reservationNo) {
        ItemStockReservationDTO current;
        try {
            current = itemClient.getPointsStockReservation(reservationNo);
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
}
