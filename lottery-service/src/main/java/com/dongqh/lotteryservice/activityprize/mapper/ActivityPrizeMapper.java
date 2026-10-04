package com.dongqh.lotteryservice.activityprize.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.lotteryservice.activityprize.entity.ActivityPrizeRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ActivityPrizeMapper extends BaseMapper<ActivityPrizeRow> {

    @Select("""
            SELECT * FROM marketing_activity_prize
            WHERE activity_id = #{activityId} AND prize_id = #{prizeId}
            FOR UPDATE
            """)
    ActivityPrizeRow lockByActivityAndPrize(@Param("activityId") long activityId,
                                            @Param("prizeId") long prizeId);
}
