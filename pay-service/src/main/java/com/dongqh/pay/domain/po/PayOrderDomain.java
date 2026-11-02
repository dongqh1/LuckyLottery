package com.dongqh.pay.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pay_order_domain")
public class PayOrderDomain {
    @TableId(value = "pay_order_id", type = IdType.INPUT)
    private Long payOrderId;
    private Long bizOrderNo;
    private String orderDomain;
    private LocalDateTime createdAt;
}
