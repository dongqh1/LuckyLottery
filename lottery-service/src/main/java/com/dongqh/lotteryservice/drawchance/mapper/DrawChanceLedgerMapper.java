package com.dongqh.lotteryservice.drawchance.mapper;

import com.dongqh.lotteryservice.drawchance.entity.DrawChanceLedgerRow;
import com.dongqh.lotteryservice.drawchance.enums.DrawChanceBusinessType;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

public interface DrawChanceLedgerMapper {
    @Insert("""
            INSERT INTO draw_chance_ledger(user_id,business_type,business_id,direction,amount,available_after,reserved_after)
            VALUES(#{userId},#{businessType},#{businessId},#{direction},#{amount},#{availableAfter},#{reservedAfter})
            """)
    int insert(DrawChanceLedgerRow ledger);

    @Select("SELECT * FROM draw_chance_ledger WHERE business_type=#{type} AND business_id=#{businessId}")
    DrawChanceLedgerRow selectBusiness(@Param("type") DrawChanceBusinessType type,
                                       @Param("businessId") String businessId);
}
