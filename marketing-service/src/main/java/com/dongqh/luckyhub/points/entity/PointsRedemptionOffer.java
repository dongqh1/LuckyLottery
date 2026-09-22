package com.dongqh.luckyhub.points.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("points_redemption_offer")
public class PointsRedemptionOffer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hmallItemId;
    private Long unitPoints;
    private Integer availableStock;
    private String itemName;
    private String imageUrl;
    private Integer cashPriceCent;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
