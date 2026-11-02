package com.dongqh.item.controller;

import com.dongqh.hmapi.dto.ItemStockReservationDTO;
import com.dongqh.hmapi.dto.InternalServiceErrorDTO;
import com.dongqh.hmapi.dto.RedeemableSkuDTO;
import com.dongqh.hmapi.dto.ReservePointsStockRequest;
import com.dongqh.item.catalog.PointsSkuCatalogService;
import com.dongqh.item.domain.model.catalog.RedeemableSkuSnapshot;
import com.dongqh.item.domain.channel.inventory.dto.ReserveChannelStockCommand;
import com.dongqh.item.domain.channel.inventory.enums.ChannelInventoryErrorCode;
import com.dongqh.item.inventory.channel.exception.InventoryDomainException;
import com.dongqh.item.domain.channel.inventory.service.ChannelInventoryService;
import com.dongqh.item.domain.channel.inventory.vo.ChannelInventoryView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal")
public class InternalItemPointsController {

    private static final String POINTS_CHANNEL = "POINTS";

    private final PointsSkuCatalogService catalog;
    private final ChannelInventoryService inventory;

    public InternalItemPointsController(PointsSkuCatalogService catalog, ChannelInventoryService inventory) {
        this.catalog = catalog;
        this.inventory = inventory;
    }

    @GetMapping("/catalog/skus/{skuId}/redemption")
    public RedeemableSkuDTO getRedeemableSku(@PathVariable("skuId") long skuId) {
        RedeemableSkuSnapshot snapshot = catalog.findRedeemableSku(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new RedeemableSkuDTO(snapshot.skuId(), snapshot.productCode(), snapshot.productName(),
                snapshot.skuCode(), snapshot.skuName(), snapshot.productType().name(), snapshot.imageUrl(),
                snapshot.pointsPrice());
    }

    @PostMapping("/inventory/points/reservations")
    public ItemStockReservationDTO reserve(@RequestBody ReservePointsStockRequest request) {
        if (request == null || request.skuId() == null || request.skuId() <= 0
                || request.quantity() == null || request.quantity() <= 0
                || request.reservationNo() == null || request.reservationNo().isBlank()) {
            throw new InventoryDomainException(ChannelInventoryErrorCode.INVENTORY_REQUEST_INVALID);
        }
        ChannelInventoryView reserved = inventory.reserve(new ReserveChannelStockCommand(
                request.skuId(), POINTS_CHANNEL, request.quantity(), request.reservationNo()));
        return view(reserved);
    }

    @PostMapping("/inventory/points/reservations/{reservationNo}/confirm")
    public ItemStockReservationDTO confirm(@PathVariable("reservationNo") String reservationNo) {
        return view(inventory.confirm(reservationNo));
    }

    @PostMapping("/inventory/points/reservations/{reservationNo}/release")
    public ItemStockReservationDTO release(@PathVariable("reservationNo") String reservationNo) {
        return view(inventory.release(reservationNo));
    }

    @PostMapping("/inventory/points/reservations/{reservationNo}/reverse")
    public ItemStockReservationDTO reverse(@PathVariable("reservationNo") String reservationNo) {
        return view(inventory.reverseConfirmed(reservationNo));
    }

    @GetMapping("/inventory/points/reservations/{reservationNo}")
    public ItemStockReservationDTO getReservation(@PathVariable("reservationNo") String reservationNo) {
        return view(inventory.getReservation(reservationNo));
    }

    @ExceptionHandler(InventoryDomainException.class)
    public ResponseEntity<InternalServiceErrorDTO> handleInventoryError(InventoryDomainException exception) {
        return ResponseEntity.status(exception.errorCode().httpStatus())
                .body(new InternalServiceErrorDTO(exception.errorCode().name(), exception.errorCode().message()));
    }

    private ItemStockReservationDTO view(ChannelInventoryView view) {
        return new ItemStockReservationDTO(view.skuId(), view.channelCode(), view.reservationNo() == null
                ? null : view.reservationQuantity(), view.reservationNo(),
                view.reservationStatus() == null ? null : view.reservationStatus().name());
    }
}
