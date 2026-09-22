package com.dongqh.tradeservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.tradeservice.domain.po.Order;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
public interface OrderMapper extends BaseMapper<Order> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM `order` WHERE id = #{id} FOR UPDATE")
    Order selectByIdForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);

}
