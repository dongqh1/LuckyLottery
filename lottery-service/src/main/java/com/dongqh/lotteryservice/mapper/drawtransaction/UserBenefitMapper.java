package com.dongqh.lotteryservice.mapper.drawtransaction;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.enums.prizecatalog.PrizeType;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface UserBenefitMapper extends BaseMapper<UserBenefitRow> {

    @Select("SELECT * FROM user_benefit WHERE id = #{benefitId} FOR UPDATE")
    UserBenefitRow selectByIdForUpdate(@Param("benefitId") long benefitId);

    @Select("""
            <script>
            SELECT * FROM user_benefit
            WHERE user_id = #{userId}
            <if test="status != null">AND status = #{status}</if>
            <if test="prizeType != null">AND prize_type = #{prizeType}</if>
            <if test="startTime != null">AND obtained_at &gt;= #{startTime}</if>
            <if test="endTime != null">AND obtained_at &lt; #{endTime}</if>
            ORDER BY obtained_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<UserBenefitRow> selectMinePage(@Param("userId") long userId,
                                        @Param("status") BenefitStatus status,
                                        @Param("prizeType") PrizeType prizeType,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("limit") long limit,
                                        @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM user_benefit
            WHERE user_id = #{userId}
            <if test="status != null">AND status = #{status}</if>
            <if test="prizeType != null">AND prize_type = #{prizeType}</if>
            <if test="startTime != null">AND obtained_at &gt;= #{startTime}</if>
            <if test="endTime != null">AND obtained_at &lt; #{endTime}</if>
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
            SET status = 'AVAILABLE', grant_error = NULL, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{benefitId} AND status IN ('PENDING','GRANT_FAILED')
            """)
    int markAvailableAfterFulfillment(@Param("benefitId") long benefitId);

    @Update("""
            UPDATE user_benefit
            SET status = 'GRANT_FAILED', grant_error = #{safeError}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{benefitId} AND status IN ('PENDING','GRANT_FAILED')
            """)
    int markGrantFailedAfterFulfillment(@Param("benefitId") long benefitId,
                                        @Param("safeError") String safeError);

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

    @Update("""
            UPDATE user_benefit
            SET fulfillment_no = #{fulfillmentNo}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{benefitId} AND status IN ('PENDING','GRANT_FAILED')
              AND (fulfillment_no IS NULL OR fulfillment_no = #{fulfillmentNo})
            """)
    int bindFulfillmentNo(@Param("benefitId") long benefitId,
                          @Param("fulfillmentNo") String fulfillmentNo);
}
