package com.dongqh.trade.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trade_membership_order")
public class TradeMembershipOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String requestKey;
    private Long membershipProductId;
    private String productCode;
    private String productName;
    private String membershipLevel;
    private Integer durationDays;
    private Integer totalFee;
    private Integer discountBasisPoints;
    private Integer dailyDrawBonus;
    private Integer pointsMultiplierBasisPoints;
    private Integer status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
