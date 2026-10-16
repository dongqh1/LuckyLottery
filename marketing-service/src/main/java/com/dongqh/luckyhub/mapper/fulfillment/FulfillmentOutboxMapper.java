package com.dongqh.luckyhub.mapper.fulfillment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.messaging.fulfillment.outbox.FulfillmentOutbox;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface FulfillmentOutboxMapper extends BaseMapper<FulfillmentOutbox> {
    @Select("""
            SELECT * FROM fulfillment_outbox
            WHERE status = 'PENDING'
               OR (status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= #{now}))
               OR (status = 'PROCESSING' AND claim_until <= #{now})
            ORDER BY id LIMIT #{limit}
            """)
    List<FulfillmentOutbox> selectRelayCandidates(@Param("now") LocalDateTime now,@Param("limit") int limit);

    @Update("""
            UPDATE fulfillment_outbox SET status='PROCESSING',claim_token=#{claimToken},claim_until=#{claimUntil}
            WHERE id=#{id} AND (status='PENDING'
              OR (status='FAILED' AND (next_retry_at IS NULL OR next_retry_at<=#{now}))
              OR (status='PROCESSING' AND claim_until<=#{now}))
            """)
    int claimForRelay(@Param("id") long id,@Param("claimToken") String claimToken,@Param("now") LocalDateTime now,@Param("claimUntil") LocalDateTime claimUntil);

    @Update("""
            UPDATE fulfillment_outbox SET status='SENT',sent_at=#{sentAt},last_error=NULL,next_retry_at=NULL,claim_token=NULL,claim_until=NULL
            WHERE id=#{id} AND status='PROCESSING' AND claim_token=#{claimToken}
            """)
    int markSent(@Param("id") long id,@Param("claimToken") String claimToken,@Param("sentAt") LocalDateTime sentAt);

    @Update("""
            UPDATE fulfillment_outbox SET status='FAILED',retry_count=retry_count+1,last_error=#{error},next_retry_at=#{nextRetryAt},claim_token=NULL,claim_until=NULL
            WHERE id=#{id} AND status='PROCESSING' AND claim_token=#{claimToken}
            """)
    int markFailed(@Param("id") long id,@Param("claimToken") String claimToken,@Param("error") String error,@Param("nextRetryAt") LocalDateTime nextRetryAt);
}
