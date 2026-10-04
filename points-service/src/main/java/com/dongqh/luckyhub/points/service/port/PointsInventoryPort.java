package com.dongqh.luckyhub.points.service.port;

public interface PointsInventoryPort {
    void reserve(long skuId, int quantity, String reservationNo);

    void confirm(String reservationNo);

    void release(String reservationNo);

    void reverseConfirmed(String reservationNo);

    void compensate(String reservationNo);
}
