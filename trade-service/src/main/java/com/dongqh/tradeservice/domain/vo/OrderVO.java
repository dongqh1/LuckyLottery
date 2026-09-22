package com.dongqh.tradeservice.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "订单页面VO")
public class OrderVO {
    @ApiModelProperty("订单id")
    private Long id;
    @ApiModelProperty("总金额，单位为分")
    private Integer totalFee;
    @ApiModelProperty("商品原价总额，单位为分")
    private Integer originalFee;
    @ApiModelProperty("会员优惠金额，单位为分")
    private Integer membershipDiscountFee;
    @ApiModelProperty("下单时冻结的会员等级")
    private String membershipLevel;
    @ApiModelProperty("下单时冻结的会员折扣基点，10000 表示不打折")
    private Integer membershipDiscountBasisPoints;
    @ApiModelProperty("用户优惠券id")
    private Long couponId;
    @ApiModelProperty("优惠券模板id快照")
    private Long couponTemplateId;
    @ApiModelProperty("优惠券名称快照")
    private String couponName;
    @ApiModelProperty("优惠券优惠金额，单位为分")
    private Integer couponDiscountFee;
    @ApiModelProperty("支付类型，1、支付宝，2、微信，3、扣减余额")
    private Integer paymentType;
    @ApiModelProperty("用户id")
    private Long userId;
    @ApiModelProperty("订单的状态，1、未付款 2、已付款,未发货 3、已发货,未确认 4、确认收货，交易成功 5、交易取消，订单关闭 6、交易结束，已评价")
    private Integer status;
    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
    @ApiModelProperty("支付时间")
    private LocalDateTime payTime;
    @ApiModelProperty("发货时间")
    private LocalDateTime consignTime;
    @ApiModelProperty("交易完成时间")
    private LocalDateTime endTime;
    @ApiModelProperty("交易关闭时间")
    private LocalDateTime closeTime;
    @ApiModelProperty("评价时间")
    private LocalDateTime commentTime;
    @ApiModelProperty("订单配送详情")
    private OrderDeliveryVO delivery;
}
