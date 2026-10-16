package com.dongqh.luckyhub.mapper.points;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.domain.po.points.PointsLedger;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

public interface PointsLedgerMapper extends BaseMapper<PointsLedger> {

    @Insert("""
            INSERT IGNORE INTO points_ledger (
                user_id, business_type, business_id, direction, amount,
                balance_after, reversal_of_ledger_id, remark, created_at
            ) VALUES (
                #{ledger.userId}, #{ledger.businessType}, #{ledger.businessId},
                #{ledger.direction}, #{ledger.amount}, #{ledger.balanceAfter},
                #{ledger.reversalOfLedgerId}, #{ledger.remark}, CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "ledger.id")
    int claim(@Param("ledger") PointsLedger ledger);
}
