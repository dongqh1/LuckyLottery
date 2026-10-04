package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.messaging.FulfillmentTaskReadyEvent;
import com.dongqh.fulfillmentservice.service.port.FulfillmentTaskReadyOutbox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public class JdbcFulfillmentTaskReadyOutbox implements FulfillmentTaskReadyOutbox {

    public static final String TASK_READY_EVENT = "FULFILLMENT_TASK_READY";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public JdbcFulfillmentTaskReadyOutbox(JdbcTemplate jdbc, ObjectMapper json, Clock fulfillmentClock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = fulfillmentClock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendTaskReady(String fulfillmentNo) {
        String eventId = UUID.randomUUID().toString();
        FulfillmentTaskReadyEvent event = new FulfillmentTaskReadyEvent(
                eventId,
                fulfillmentNo,
                LocalDateTime.now(clock)
        );
        try {
            jdbc.update(
                    "INSERT INTO fulfillment_outbox (event_id, fulfillment_no, event_type, payload, status) "
                            + "VALUES (?, ?, ?, ?, 'PENDING')",
                    eventId,
                    fulfillmentNo,
                    TASK_READY_EVENT,
                    json.writeValueAsString(event)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("履约任务触发事件序列化失败", exception);
        }
    }
}
