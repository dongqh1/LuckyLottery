package com.dongqh.item.domain.channel.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.item.domain.channel.inventory.entity.InventoryReservation;
import com.dongqh.item.domain.channel.inventory.enums.InventoryReservationStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface InventoryReservationMapper extends BaseMapper<InventoryReservation> {
    @Update("""
            UPDATE inventory_reservation
            SET status = #{targetStatus}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE reservation_no = #{reservationNo} AND status = 'RESERVED'
            """)
    int transitionReserved(@Param("reservationNo") String reservationNo,
                           @Param("targetStatus") InventoryReservationStatus targetStatus);

    @Update("""
            UPDATE inventory_reservation
            SET status = 'REVERSED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE reservation_no = #{reservationNo} AND status = 'CONFIRMED'
            """)
    int reverseConfirmed(@Param("reservationNo") String reservationNo);
}
