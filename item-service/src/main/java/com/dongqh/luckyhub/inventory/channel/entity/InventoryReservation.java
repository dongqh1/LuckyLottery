package com.dongqh.luckyhub.domain.channel.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.domain.channel.inventory.enums.InventoryReservationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("inventory_reservation")
public class InventoryReservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reservationNo;
    private Long skuId;
    private String channelCode;
    private Integer quantity;
    private InventoryReservationStatus status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
