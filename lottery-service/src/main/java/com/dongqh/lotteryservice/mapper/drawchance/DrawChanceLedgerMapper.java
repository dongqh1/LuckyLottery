package com.dongqh.lotteryservice.mapper.drawchance;

import com.dongqh.lotteryservice.domain.po.drawchance.DrawChanceLedgerRow;
import com.dongqh.lotteryservice.enums.drawchance.DrawChanceBusinessType;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import java.util.List;

public interface DrawChanceLedgerMapper {
    @Insert("""
            INSERT INTO draw_chance_ledger(user_id,business_type,business_id,direction,amount,available_after,reserved_after)
            VALUES(#{userId},#{businessType},#{businessId},#{direction},#{amount},#{availableAfter},#{reservedAfter})
            """)
    int insert(DrawChanceLedgerRow ledger);

    @Insert("""
            INSERT INTO draw_chance_ledger(user_id,operator_user_id,business_type,business_id,direction,amount,
                                           available_after,reserved_after,reason)
            VALUES(#{userId},#{operatorUserId},#{businessType},#{businessId},#{direction},#{amount},
                   #{availableAfter},#{reservedAfter},#{reason})
            """)
    int insertAdjustment(DrawChanceLedgerRow ledger);

    @Select("SELECT * FROM draw_chance_ledger WHERE business_type=#{type} AND business_id=#{businessId}")
    DrawChanceLedgerRow selectBusiness(@Param("type") DrawChanceBusinessType type,
                                       @Param("businessId") String businessId);

    @Select("SELECT * FROM draw_chance_ledger WHERE user_id=#{userId} ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<DrawChanceLedgerRow> selectUserPage(@Param("userId") long userId,
                                             @Param("limit") long limit,
                                             @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM draw_chance_ledger WHERE user_id=#{userId}")
    long countUser(@Param("userId") long userId);
}
