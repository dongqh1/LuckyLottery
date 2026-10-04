package com.dongqh.luckyhub.points.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.points.enums.PointsProductType;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStatus;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStep;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("points_redemption_order")
public class PointsRedemptionOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String redemptionNo;
    private Long userId;
    private Long skuId;
    private Long offerId;
    private Long hmallItemId;
    private Integer quantity;
    private Long unitPoints;
    private Long totalPoints;
    private Integer cashPriceCent;
    private String productCode;
    private String productName;
    private String skuCode;
    private String skuName;
    private PointsProductType productType;
    private String imageUrl;
    private PointsRedemptionStatus status;
    private PointsRedemptionStep processStep;
    private String requestFingerprint;
    private Long addressId;
    private Long shippingOrderId;
    private String reversalNo;
    private String failureReason;
    private String originalErrorCode;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
