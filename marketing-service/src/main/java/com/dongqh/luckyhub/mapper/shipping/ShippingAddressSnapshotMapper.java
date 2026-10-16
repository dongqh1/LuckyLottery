package com.dongqh.luckyhub.mapper.shipping;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.domain.po.shipping.ShippingAddressSnapshot;
import com.dongqh.luckyhub.enums.shipping.ShippingSourceType;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ShippingAddressSnapshotMapper extends BaseMapper<ShippingAddressSnapshot> {
    @Select("SELECT * FROM shipping_address_snapshot WHERE source_type=#{type} AND source_id=#{sourceId}")
    ShippingAddressSnapshot selectBySource(@Param("type") ShippingSourceType type,
                                           @Param("sourceId") String sourceId);
}
