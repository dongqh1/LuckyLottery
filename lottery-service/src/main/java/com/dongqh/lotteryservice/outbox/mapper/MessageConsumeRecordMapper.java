package com.dongqh.lotteryservice.outbox.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MessageConsumeRecordMapper {

    @Select("SELECT COUNT(*) FROM message_consume_record WHERE event_id = #{eventId} AND consumer_name = #{consumerName}")
    int countByEventAndConsumer(@Param("eventId") String eventId,
                                @Param("consumerName") String consumerName);

    @Insert("""
            INSERT INTO message_consume_record(event_id, consumer_name)
            VALUES(#{eventId}, #{consumerName})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("consumerName") String consumerName);
}
