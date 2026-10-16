package com.dongqh.luckyhub.fulfillment;

import com.dongqh.luckyhub.messaging.fulfillment.event.FulfillmentTaskReadyEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FulfillmentOutboxSchemaTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsLeaseBasedFulfillmentOutbox() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'fulfillment_outbox'
                """, Integer.class)).isEqualTo(13);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'fulfillment_outbox'
                  AND index_name = 'uk_fulfillment_outbox_event_id'
                """, Integer.class)).isOne();
    }

    @Test
    void taskReadyEventRejectsMissingIdentity() {
        assertThatThrownBy(() -> new FulfillmentTaskReadyEvent(
                " ", "TASK-1", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
