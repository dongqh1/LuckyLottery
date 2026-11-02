package com.dongqh.lottery.outbox.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface LotteryRewardQuarantineMapper {

    @Insert("""
            INSERT INTO lottery_reward_quarantine
                (event_id, request_id, order_id, draw_record_id, benefit_id, prize_id,
                 reward_definition_id, reason_code, status)
            VALUES
                (#{eventId}, #{requestId}, #{orderId}, #{drawRecordId}, #{benefitId}, #{prizeId},
                 #{rewardDefinitionId}, #{reasonCode}, 'OPEN')
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("requestId") String requestId,
                       @Param("orderId") Long orderId,
                       @Param("drawRecordId") Long drawRecordId,
                       @Param("benefitId") Long benefitId,
                       @Param("prizeId") Long prizeId,
                       @Param("rewardDefinitionId") Long rewardDefinitionId,
                       @Param("reasonCode") String reasonCode);
}
