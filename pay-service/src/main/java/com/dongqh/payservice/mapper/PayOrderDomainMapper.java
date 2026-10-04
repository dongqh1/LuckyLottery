package com.dongqh.payservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.payservice.domain.po.PayOrderDomain;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PayOrderDomainMapper extends BaseMapper<PayOrderDomain> {
    @Select("SELECT * FROM pay_order_domain WHERE pay_order_id = #{payOrderId}")
    PayOrderDomain selectByPayOrderId(@Param("payOrderId") Long payOrderId);
}
