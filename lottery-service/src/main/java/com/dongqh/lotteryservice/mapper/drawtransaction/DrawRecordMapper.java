package com.dongqh.lotteryservice.mapper.drawtransaction;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.enums.drawtransaction.DrawResultType;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface DrawRecordMapper extends BaseMapper<DrawRecordRow> {

    @Select("""
            SELECT r.*, b.id AS benefit_id
            FROM lottery_draw_record r
            LEFT JOIN user_benefit b ON b.draw_record_id = r.id
            WHERE r.order_id = #{orderId}
            ORDER BY r.sequence_no ASC, r.id ASC
            """)
    @Results(id = "drawRecordWithBenefit", value = {
            @Result(property = "benefitId", column = "benefit_id")
    })
    List<DrawRecordRow> selectByOrderId(@Param("orderId") long orderId);

    @Select("""
            <script>
            SELECT r.*, b.id AS benefit_id
            FROM lottery_draw_record r
            LEFT JOIN user_benefit b ON b.draw_record_id = r.id
            WHERE r.user_id = #{userId}
            <if test="activityId != null">AND r.activity_id = #{activityId}</if>
            <if test="resultType != null">AND r.result_type = #{resultType}</if>
            <if test="startTime != null">AND r.draw_time &gt;= #{startTime}</if>
            <if test="endTime != null">AND r.draw_time &lt; #{endTime}</if>
            ORDER BY r.draw_time DESC, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @org.apache.ibatis.annotations.ResultMap("drawRecordWithBenefit")
    List<DrawRecordRow> selectMinePage(@Param("userId") long userId, @Param("activityId") Long activityId,
                                       @Param("resultType") DrawResultType resultType,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime,
                                       @Param("limit") long limit, @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM lottery_draw_record r
            WHERE r.user_id = #{userId}
            <if test="activityId != null">AND r.activity_id = #{activityId}</if>
            <if test="resultType != null">AND r.result_type = #{resultType}</if>
            <if test="startTime != null">AND r.draw_time &gt;= #{startTime}</if>
            <if test="endTime != null">AND r.draw_time &lt; #{endTime}</if>
            </script>
            """)
    long countMine(@Param("userId") long userId, @Param("activityId") Long activityId,
                   @Param("resultType") DrawResultType resultType,
                   @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("""
            <script>
            SELECT r.*, b.id AS benefit_id
            FROM lottery_draw_record r
            LEFT JOIN user_benefit b ON b.draw_record_id = r.id
            WHERE 1=1
            <if test="userId != null">AND r.user_id = #{userId}</if>
            <if test="activityId != null">AND r.activity_id = #{activityId}</if>
            <if test="resultType != null">AND r.result_type = #{resultType}</if>
            <if test="startTime != null">AND r.draw_time &gt;= #{startTime}</if>
            <if test="endTime != null">AND r.draw_time &lt; #{endTime}</if>
            ORDER BY r.draw_time DESC, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @org.apache.ibatis.annotations.ResultMap("drawRecordWithBenefit")
    List<DrawRecordRow> selectAdminPage(@Param("userId") Long userId, @Param("activityId") Long activityId,
                                        @Param("resultType") DrawResultType resultType,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("limit") long limit, @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM lottery_draw_record r
            WHERE 1=1
            <if test="userId != null">AND r.user_id = #{userId}</if>
            <if test="activityId != null">AND r.activity_id = #{activityId}</if>
            <if test="resultType != null">AND r.result_type = #{resultType}</if>
            <if test="startTime != null">AND r.draw_time &gt;= #{startTime}</if>
            <if test="endTime != null">AND r.draw_time &lt; #{endTime}</if>
            </script>
            """)
    long countAdmin(@Param("userId") Long userId, @Param("activityId") Long activityId,
                    @Param("resultType") DrawResultType resultType,
                    @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("""
            <script>
            SELECT r.*, b.id AS benefit_id
            FROM lottery_draw_record r
            LEFT JOIN user_benefit b ON b.draw_record_id = r.id
            WHERE r.order_id IN
            <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">
                #{orderId}
            </foreach>
            ORDER BY r.order_id, r.sequence_no, r.id
            </script>
            """)
    @org.apache.ibatis.annotations.ResultMap("drawRecordWithBenefit")
    List<DrawRecordRow> selectByOrderIds(@Param("orderIds") List<Long> orderIds);
}
