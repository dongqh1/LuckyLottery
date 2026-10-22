package com.dongqh.lotteryservice.mapper.drawtransaction;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface UserBenefitMapper extends BaseMapper<UserBenefitRow> {

    @Select("""
            SELECT b.*, p.prize_type AS current_prize_type, p.prize_name AS current_prize_name,
                   p.image_url AS current_prize_image_url, rd.id AS current_reward_definition_id,
                   rd.reward_type AS current_reward_type, rd.target_id AS current_reward_target_id,
                   rd.quantity AS current_reward_quantity
            FROM user_benefit b
            LEFT JOIN marketing_prize p ON p.id = b.prize_id
            LEFT JOIN reward_definition rd ON rd.id = p.reward_definition_id
            WHERE b.id = #{benefitId} FOR UPDATE
            """)
    @Results(id = "benefitWithCurrentPrize", value = {
            @Result(property = "prizeType", column = "current_prize_type"),
            @Result(property = "prizeName", column = "current_prize_name"),
            @Result(property = "prizeImageUrl", column = "current_prize_image_url"),
            @Result(property = "rewardDefinitionId", column = "current_reward_definition_id"),
            @Result(property = "rewardType", column = "current_reward_type"),
            @Result(property = "rewardTargetId", column = "current_reward_target_id"),
            @Result(property = "rewardQuantity", column = "current_reward_quantity")
    })
    UserBenefitRow selectByIdForUpdate(@Param("benefitId") long benefitId);

    @Select("""
            SELECT b.*, p.prize_type AS current_prize_type, p.prize_name AS current_prize_name,
                   p.image_url AS current_prize_image_url, rd.id AS current_reward_definition_id,
                   rd.reward_type AS current_reward_type, rd.target_id AS current_reward_target_id,
                   rd.quantity AS current_reward_quantity
            FROM user_benefit b
            LEFT JOIN marketing_prize p ON p.id = b.prize_id
            LEFT JOIN reward_definition rd ON rd.id = p.reward_definition_id
            WHERE b.id = #{benefitId}
            """)
    @ResultMap("benefitWithCurrentPrize")
    UserBenefitRow selectByIdWithCurrentPrize(@Param("benefitId") long benefitId);

    @Select("""
            <script>
            SELECT b.*, p.prize_type AS current_prize_type, p.prize_name AS current_prize_name,
                   p.image_url AS current_prize_image_url, rd.id AS current_reward_definition_id,
                   rd.reward_type AS current_reward_type, rd.target_id AS current_reward_target_id,
                   rd.quantity AS current_reward_quantity
            FROM user_benefit b
            LEFT JOIN marketing_prize p ON p.id = b.prize_id
            LEFT JOIN reward_definition rd ON rd.id = p.reward_definition_id
            WHERE b.user_id = #{userId}
            <if test="status != null">AND b.status = #{status}</if>
            <if test="prizeType != null">AND p.prize_type = #{prizeType}</if>
            <if test="startTime != null">AND b.obtained_at &gt;= #{startTime}</if>
            <if test="endTime != null">AND b.obtained_at &lt; #{endTime}</if>
            ORDER BY b.obtained_at DESC, b.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ResultMap("benefitWithCurrentPrize")
    List<UserBenefitRow> selectMinePage(@Param("userId") long userId,
                                        @Param("status") BenefitStatus status,
                                        @Param("prizeType") PrizeType prizeType,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("limit") long limit,
                                        @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM user_benefit b
            LEFT JOIN marketing_prize p ON p.id = b.prize_id
            LEFT JOIN reward_definition rd ON rd.id = p.reward_definition_id
            WHERE b.user_id = #{userId}
            <if test="status != null">AND b.status = #{status}</if>
            <if test="prizeType != null">AND p.prize_type = #{prizeType}</if>
            <if test="startTime != null">AND b.obtained_at &gt;= #{startTime}</if>
            <if test="endTime != null">AND b.obtained_at &lt; #{endTime}</if>
            </script>
            """)
    long countMine(@Param("userId") long userId,
                   @Param("status") BenefitStatus status,
                   @Param("prizeType") PrizeType prizeType,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT id
            FROM user_benefit
            WHERE status = 'CLAIM_PENDING'
              AND claim_deadline IS NOT NULL
              AND claim_deadline <= #{now}
              AND id > #{afterId}
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<Long> selectDueClaimIdsAfter(@Param("now") LocalDateTime now,
                                     @Param("afterId") long afterId,
                                     @Param("limit") int limit);

    @Update("""
            UPDATE user_benefit
            SET status = #{target}, grant_error = NULL, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{benefitId} AND status = #{current}
            """)
    int transitionStatus(@Param("benefitId") long benefitId,
                         @Param("current") BenefitStatus current,
                         @Param("target") BenefitStatus target);

    @Update("""
            UPDATE user_benefit
            SET status = 'CLAIM_PENDING', claim_deadline = #{deadline}, grant_error = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{benefitId} AND status = #{current}
            """)
    int markClaimPending(@Param("benefitId") long benefitId,
                         @Param("current") BenefitStatus current,
                         @Param("deadline") java.time.LocalDateTime deadline);

    @Update("""
            UPDATE user_benefit
            SET status = 'FULFILLING', claimed_at = #{claimedAt}, shipping_order_id = #{shippingOrderId},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{benefitId} AND status = 'CLAIM_PENDING' AND shipping_order_id IS NULL
              AND claim_deadline IS NOT NULL AND claim_deadline > #{claimedAt}
            """)
    int markClaimed(@Param("benefitId") long benefitId,
                    @Param("shippingOrderId") long shippingOrderId,
                    @Param("claimedAt") LocalDateTime claimedAt);

    @Update("""
            UPDATE user_benefit
            SET status = 'CLAIM_EXPIRED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{benefitId}
              AND status = 'CLAIM_PENDING'
              AND claim_deadline IS NOT NULL
              AND claim_deadline <= #{now}
            """)
    int markClaimExpired(@Param("benefitId") long benefitId, @Param("now") LocalDateTime now);

}
