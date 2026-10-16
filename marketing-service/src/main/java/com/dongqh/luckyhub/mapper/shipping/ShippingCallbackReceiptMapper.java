package com.dongqh.luckyhub.mapper.shipping;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.domain.po.shipping.ShippingCallbackReceipt;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ShippingCallbackReceiptMapper extends BaseMapper<ShippingCallbackReceipt> {
    @Select("SELECT * FROM shipping_callback_receipt WHERE callback_id=#{callbackId}")
    ShippingCallbackReceipt selectByCallbackId(@Param("callbackId") String callbackId);
}
