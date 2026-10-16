package com.dongqh.luckyhub.domain.channel.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.domain.channel.inventory.enums.InventoryOperation;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("inventory_ledger")
public class InventoryLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String businessNo;
    private Long skuId;
    private Long hmallItemId;
    private String channelCode;
    private InventoryOperation operation;
    private Integer quantity;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
