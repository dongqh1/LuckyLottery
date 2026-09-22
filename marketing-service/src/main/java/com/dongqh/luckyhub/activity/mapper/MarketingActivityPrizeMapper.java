package com.dongqh.luckyhub.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.lottery.vo.LotteryPrizeView;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MarketingActivityPrizeMapper extends BaseMapper<MarketingActivityPrize> {
    @Select("""
            SELECT * FROM marketing_activity_prize
            WHERE activity_id=#{activityId} AND prize_id=#{prizeId} FOR UPDATE
            """)
    MarketingActivityPrize lockByActivityAndPrize(@Param("activityId") long activityId,
                                                  @Param("prizeId") long prizeId);

    @Select("""
            SELECT p.id AS prize_id,
                   p.prize_name AS prize_name,
                   p.prize_type AS prize_type,
                   p.image_url AS image_url,
                   ap.sort_order AS sort_order,
                   CASE WHEN rd.reward_type = 'PRODUCT' THEN rd.target_id END AS hmall_item_id
            FROM marketing_activity_prize ap
            JOIN marketing_prize p ON p.id = ap.prize_id
            LEFT JOIN reward_definition rd ON rd.id = p.reward_definition_id
            WHERE ap.activity_id = #{activityId}
              AND ap.remaining_stock > 0
              AND p.status = 1
            ORDER BY ap.sort_order ASC, ap.id ASC
            LIMIT 100
            """)
    List<LotteryPrizeView> selectVisiblePrizeViews(@Param("activityId") long activityId);
}
