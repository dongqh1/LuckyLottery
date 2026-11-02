package com.dongqh.lottery.mapper.drawchance;

import com.dongqh.lottery.domain.po.drawchance.DrawChanceReservationRow;
import com.dongqh.lottery.enums.drawchance.DrawChanceReservationStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DrawChanceReservationMapper {
    @Insert("""
            INSERT IGNORE INTO draw_chance_reservation(request_id,user_id,activity_id,draw_date,draw_count,bonus_reserved,status)
            VALUES(#{r.requestId},#{r.userId},#{r.activityId},#{r.drawDate},#{r.drawCount},0,'RESERVED')
            """)
    int insertIfAbsent(@Param("r") DrawChanceReservationRow reservation);

    @Select("SELECT * FROM draw_chance_reservation WHERE request_id=#{requestId} FOR UPDATE")
    DrawChanceReservationRow lockByRequestId(@Param("requestId") String requestId);

    @Update("UPDATE draw_chance_reservation SET bonus_reserved=#{bonus} WHERE id=#{id} AND status='RESERVED'")
    int setBonus(@Param("id") long id, @Param("bonus") long bonus);

    @Update("UPDATE draw_chance_reservation SET status=#{status},settled_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status='RESERVED'")
    int settle(@Param("id") long id, @Param("status") DrawChanceReservationStatus status);

    @Select("""
            SELECT COALESCE(SUM(bonus_reserved),0) FROM draw_chance_reservation
            WHERE user_id=#{userId} AND activity_id=#{activityId} AND draw_date=#{drawDate}
              AND status IN ('RESERVED','CONFIRMED')
            """)
    long sumActiveBonus(@Param("userId") long userId, @Param("activityId") long activityId,
                        @Param("drawDate") LocalDate drawDate);

    @Select("""
            SELECT r.* FROM draw_chance_reservation r
            WHERE r.status='RESERVED' AND r.created_at < #{cutoff}
              AND NOT EXISTS (
                  SELECT 1 FROM lottery_draw_order o
                  WHERE o.request_id=r.request_id AND o.status='PROCESSING'
              )
            ORDER BY r.created_at,r.id LIMIT #{limit} FOR UPDATE SKIP LOCKED
            """)
    List<DrawChanceReservationRow> lockExpired(@Param("limit") int limit,
                                               @Param("cutoff") LocalDateTime cutoff);
}
