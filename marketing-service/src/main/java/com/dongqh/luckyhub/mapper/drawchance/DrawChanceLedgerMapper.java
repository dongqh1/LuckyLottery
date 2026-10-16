package com.dongqh.luckyhub.mapper.drawchance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.domain.po.drawchance.DrawChanceLedger;
import com.dongqh.luckyhub.enums.drawchance.DrawChanceBusinessType;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DrawChanceLedgerMapper extends BaseMapper<DrawChanceLedger> {
    @Select("SELECT * FROM draw_chance_ledger WHERE business_type=#{type} AND business_id=#{businessId}")
    DrawChanceLedger selectBusiness(@Param("type") DrawChanceBusinessType type,
                                    @Param("businessId") String businessId);
}
