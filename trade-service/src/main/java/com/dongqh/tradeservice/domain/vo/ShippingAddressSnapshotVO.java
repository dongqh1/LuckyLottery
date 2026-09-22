package com.dongqh.tradeservice.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "订单收货地址快照")
public class ShippingAddressSnapshotVO {
    @ApiModelProperty("收件人")
    private String contact;
    @ApiModelProperty("收件人手机号码")
    private String mobile;
    @ApiModelProperty("省")
    private String province;
    @ApiModelProperty("市")
    private String city;
    @ApiModelProperty("区/县")
    private String town;
    @ApiModelProperty("详细地址")
    private String street;

    @ApiModelProperty("完整收货地址")
    public String getFullAddress() {
        return text(province) + text(city) + text(town) + text(street);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
