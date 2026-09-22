package com.dongqh.tradeservice.domain.dto;

import com.dongqh.hmapi.dto.OrderDetailDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel(description = "交易下单表单实体")
public class OrderFormDTO {
    @ApiModelProperty("收货地址id")
    private Long addressId;
    @ApiModelProperty("支付类型")
    private Integer paymentType;
    @ApiModelProperty("本次结算选择的用户优惠券id")
    private Long couponId;
    @ApiModelProperty("下单商品列表")
    private List<OrderDetailDTO> details;
}
