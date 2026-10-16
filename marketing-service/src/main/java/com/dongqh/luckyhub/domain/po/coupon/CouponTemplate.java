package com.dongqh.luckyhub.domain.po.coupon;

import com.baomidou.mybatisplus.annotation.*;
import com.dongqh.luckyhub.enums.coupon.CouponScopeType;
import com.dongqh.luckyhub.enums.coupon.CouponType;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @TableName("coupon_template")
public class CouponTemplate {
    @TableId(type=IdType.AUTO) private Long id;
    private String templateCode;
    private String templateName;
    private CouponType couponType;
    private Long thresholdCent;
    private Long discountCent;
    /** Historical LuckyHub product scope. New commerce must not populate this field. */
    private Long applicableProductId;
    private CouponScopeType scopeType;
    private Long hmallItemId;
    private String hmallCategory;
    private String hmallBrand;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Integer perUserLimit;
    private Boolean stackableWithMembership;
    private Integer status;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
