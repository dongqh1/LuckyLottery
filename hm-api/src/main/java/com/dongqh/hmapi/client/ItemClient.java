package com.dongqh.hmapi.client;


import com.dongqh.hmapi.client.fallback.ItemClientFallbackFactory;
import com.dongqh.hmapi.dto.ItemDTO;
import com.dongqh.hmapi.dto.ItemStockReservationDTO;
import com.dongqh.hmapi.dto.OrderDetailDTO;
import com.dongqh.hmapi.dto.RedeemableSkuDTO;
import com.dongqh.hmapi.dto.ReservePointsStockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient(value = "item-service",fallbackFactory = ItemClientFallbackFactory.class)
public interface ItemClient {
    @GetMapping("/items")
    List<ItemDTO> queryItemByIds(@RequestParam("ids") Collection<Long> ids);

    @PutMapping("/items/stock/deduct")
    void deductStock(@RequestBody List<OrderDetailDTO> items);

    @GetMapping("/internal/catalog/skus/{skuId}/redemption")
    RedeemableSkuDTO getRedeemableSku(@PathVariable("skuId") long skuId);

    @PostMapping("/internal/inventory/points/reservations")
    ItemStockReservationDTO reservePointsStock(@RequestBody ReservePointsStockRequest request);

    @PostMapping("/internal/inventory/points/reservations/{reservationNo}/confirm")
    ItemStockReservationDTO confirmPointsStock(@PathVariable("reservationNo") String reservationNo);

    @PostMapping("/internal/inventory/points/reservations/{reservationNo}/release")
    ItemStockReservationDTO releasePointsStock(@PathVariable("reservationNo") String reservationNo);

    @PostMapping("/internal/inventory/points/reservations/{reservationNo}/reverse")
    ItemStockReservationDTO reversePointsStock(@PathVariable("reservationNo") String reservationNo);

    @GetMapping("/internal/inventory/points/reservations/{reservationNo}")
    ItemStockReservationDTO getPointsStockReservation(@PathVariable("reservationNo") String reservationNo);
}
