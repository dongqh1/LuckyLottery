package com.dongqh.luckyhub.mapper.shipping;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.domain.po.shipping.ShippingTrackingEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ShippingTrackingEventMapper extends BaseMapper<ShippingTrackingEvent> {
    @Select("SELECT * FROM shipping_tracking_event WHERE shipping_order_id=#{shippingOrderId} ORDER BY event_time,id")
    List<ShippingTrackingEvent> selectByShippingOrderId(@Param("shippingOrderId") long shippingOrderId);
}
