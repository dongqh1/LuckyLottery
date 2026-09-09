package com.dongqh.hmapi.dto;

import lombok.Data;

@Data
public class AddressDTO {
    private Long id;
    private String province;
    private String city;
    private String town;
    private String mobile;
    private String street;
    private String contact;
    private Integer isDefault;
    private String notes;
    // 当前用于 trade-service 校验地址归属
    private Long userId;
}
