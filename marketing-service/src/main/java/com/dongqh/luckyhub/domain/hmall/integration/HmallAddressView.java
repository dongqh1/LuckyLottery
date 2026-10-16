package com.dongqh.luckyhub.domain.hmall.integration;

public record HmallAddressView(
        Long id,
        String province,
        String city,
        String town,
        String mobile,
        String street,
        String contact,
        Integer isDefault,
        String notes
) {
}
