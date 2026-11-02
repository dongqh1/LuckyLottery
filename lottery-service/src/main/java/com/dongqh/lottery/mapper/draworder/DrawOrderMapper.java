package com.dongqh.lottery.mapper.draworder;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.lottery.domain.po.draworder.DrawOrderRow;
import com.dongqh.lottery.enums.draworder.DrawOrderStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

public interface DrawOrderMapper extends BaseMapper<DrawOrderRow> {

    @Insert("""
            INSERT INTO lottery_draw_order
                (request_id, user_id, activity_id, draw_count, draw_date, status)
            VALUES
                (#{order.requestId}, #{order.userId}, #{order.activityId},
                 #{order.drawCount}, #{order.drawDate}, 'PROCESSING')
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertProcessingIfAbsent(@Param("order") DrawOrderRow order);

    @Select("SELECT * FROM lottery_draw_order WHERE request_id = #{requestId}")
    DrawOrderRow selectByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM lottery_draw_order WHERE request_id = #{requestId} FOR UPDATE")
    DrawOrderRow selectByRequestIdForUpdate(@Param("requestId") String requestId);

    @Update("""
            UPDATE lottery_draw_order
            SET status = 'SUCCESS', fail_reason = NULL, completed_at = #{completedAt}
            WHERE id = #{orderId} AND status = 'PROCESSING'
            """)
    int markSuccessIfProcessing(@Param("orderId") long orderId,
                                 @Param("completedAt") LocalDateTime completedAt);

    @Update("""
            UPDATE lottery_draw_order
            SET status = 'FAILED', fail_reason = #{safeReason},
                completed_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{orderId} AND status = 'PROCESSING'
            """)
    int markFailedIfProcessing(@Param("orderId") long orderId, @Param("safeReason") String safeReason);

    @Select("""
            <script>
            SELECT * FROM lottery_draw_order
            WHERE user_id = #{userId}
            <if test="activityId != null">AND activity_id = #{activityId}</if>
            <if test="status != null">AND status = #{status}</if>
            <if test="drawDate != null">AND draw_date = #{drawDate}</if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DrawOrderRow> selectMinePage(@Param("userId") long userId, @Param("activityId") Long activityId,
                                      @Param("status") DrawOrderStatus status,
                                      @Param("drawDate") LocalDate drawDate,
                                      @Param("limit") long limit, @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM lottery_draw_order
            WHERE user_id = #{userId}
            <if test="activityId != null">AND activity_id = #{activityId}</if>
            <if test="status != null">AND status = #{status}</if>
            <if test="drawDate != null">AND draw_date = #{drawDate}</if>
            </script>
            """)
    long countMine(@Param("userId") long userId, @Param("activityId") Long activityId,
                   @Param("status") DrawOrderStatus status, @Param("drawDate") LocalDate drawDate);

    @Select("""
            <script>
            SELECT * FROM lottery_draw_order
            WHERE 1=1
            <if test="userId != null">AND user_id = #{userId}</if>
            <if test="activityId != null">AND activity_id = #{activityId}</if>
            <if test="status != null">AND status = #{status}</if>
            <if test="drawDate != null">AND draw_date = #{drawDate}</if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DrawOrderRow> selectAdminPage(@Param("userId") Long userId, @Param("activityId") Long activityId,
                                       @Param("status") DrawOrderStatus status,
                                       @Param("drawDate") LocalDate drawDate,
                                       @Param("limit") long limit, @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM lottery_draw_order
            WHERE 1=1
            <if test="userId != null">AND user_id = #{userId}</if>
            <if test="activityId != null">AND activity_id = #{activityId}</if>
            <if test="status != null">AND status = #{status}</if>
            <if test="drawDate != null">AND draw_date = #{drawDate}</if>
            </script>
            """)
    long countAdmin(@Param("userId") Long userId, @Param("activityId") Long activityId,
                    @Param("status") DrawOrderStatus status, @Param("drawDate") LocalDate drawDate);
}
