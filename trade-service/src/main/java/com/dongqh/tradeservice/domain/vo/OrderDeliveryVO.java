package com.dongqh.tradeservice.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "订单配送详情")
public class OrderDeliveryVO {
    @ApiModelProperty("承运商")
    private String carrier;
    @ApiModelProperty("运单号")
    private String trackingNumber;
    @ApiModelProperty("下单时冻结的收货地址")
    private ShippingAddressSnapshotVO addressSnapshot;
}
