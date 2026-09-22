package com.dongqh.hmapi.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OrderDetailDTO {
    private Long itemId;
    private Integer num;
}
