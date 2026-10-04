package com.dongqh.tradeservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.tradeservice.domain.po.TradeMembershipOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TradeMembershipOrderMapper extends BaseMapper<TradeMembershipOrder> {

    @Select("SELECT * FROM trade_membership_order WHERE user_id = #{userId} "
            + "AND request_key = #{requestKey} LIMIT 1")
    TradeMembershipOrder selectByUserAndRequestKey(@Param("userId") Long userId,
                                                    @Param("requestKey") String requestKey);

    @Select("SELECT * FROM trade_membership_order WHERE id = #{orderId} FOR UPDATE")
    TradeMembershipOrder selectByIdForUpdate(@Param("orderId") Long orderId);
}
