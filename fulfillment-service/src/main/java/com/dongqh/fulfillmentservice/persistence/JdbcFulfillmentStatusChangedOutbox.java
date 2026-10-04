package com.dongqh.fulfillmentservice.persistence;

import com.dongqh.fulfillmentservice.service.port.FulfillmentStatusChangedOutbox;
import com.dongqh.fulfillmentservice.service.port.FulfillmentStatusChangedPublisher;
import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcFulfillmentStatusChangedOutbox implements FulfillmentStatusChangedOutbox {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcFulfillmentStatusChangedOutbox(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(FulfillmentStatusChangedEventV1 event) {
        try {
            jdbc.update("""
                    INSERT INTO fulfillment_outbox (event_id, fulfillment_no, event_type, payload, status)
                    VALUES (?, ?, ?, ?, 'PENDING')
                    """, event.eventId(), event.fulfillmentNo(), FulfillmentStatusChangedPublisher.EVENT_TYPE,
                    json.writeValueAsString(event));
        } catch (JacksonException exception) {
            throw new IllegalStateException("履约状态事件序列化失败", exception);
        }
    }
}
