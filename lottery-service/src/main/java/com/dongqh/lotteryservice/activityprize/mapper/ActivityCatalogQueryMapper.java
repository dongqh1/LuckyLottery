package com.dongqh.lotteryservice.activityprize.mapper;

import com.dongqh.lotteryservice.activitycatalog.entity.ActivityCatalogRow;
import com.dongqh.lotteryservice.activitycatalog.entity.ActivityPrizeCatalogRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityCatalogQueryMapper {

    @Select("""
            SELECT id, activity_name, description, status, start_time, end_time, daily_limit
            FROM marketing_activity
            WHERE status = 'RUNNING'
              AND start_time <= #{now}
              AND end_time >= #{now}
            ORDER BY end_time ASC, id ASC
            LIMIT 100
            """)
    @Results(id = "activityCatalogRow", value = {
            @Result(id = true, property = "id", column = "id"),
            @Result(property = "activityName", column = "activity_name"),
            @Result(property = "description", column = "description"),
            @Result(property = "status", column = "status"),
            @Result(property = "startTime", column = "start_time"),
            @Result(property = "endTime", column = "end_time"),
            @Result(property = "dailyLimit", column = "daily_limit")
    })
    List<ActivityCatalogRow> listActivities(@Param("now") LocalDateTime now);

    @Select("""
            SELECT id, activity_name, description, status, start_time, end_time, daily_limit
            FROM marketing_activity
            WHERE id = #{activityId}
            """)
    @ResultMap("activityCatalogRow")
    ActivityCatalogRow findActivityById(@Param("activityId") long activityId);

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
    @Results(id = "activityPrizeCatalogRow", value = {
            @Result(id = true, property = "prizeId", column = "prize_id"),
            @Result(property = "prizeName", column = "prize_name"),
            @Result(property = "prizeType", column = "prize_type"),
            @Result(property = "imageUrl", column = "image_url"),
            @Result(property = "sortOrder", column = "sort_order"),
            @Result(property = "hmallItemId", column = "hmall_item_id")
    })
    List<ActivityPrizeCatalogRow> listVisiblePrizes(@Param("activityId") long activityId);
}
