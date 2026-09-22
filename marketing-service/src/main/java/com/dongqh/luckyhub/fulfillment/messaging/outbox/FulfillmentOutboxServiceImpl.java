package com.dongqh.luckyhub.fulfillment.messaging.outbox;

import com.dongqh.luckyhub.fulfillment.messaging.event.FulfillmentTaskReadyEvent;
import com.dongqh.luckyhub.fulfillment.mapper.FulfillmentOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FulfillmentOutboxServiceImpl implements FulfillmentOutboxService {

    public static final String TASK_READY_EVENT = "FULFILLMENT_TASK_READY";

    private final FulfillmentOutboxMapper mapper;
    private final ObjectMapper json;

    public FulfillmentOutboxServiceImpl(FulfillmentOutboxMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendTaskReady(String fulfillmentNo) {
        String eventId = UUID.randomUUID().toString();
        FulfillmentTaskReadyEvent event = new FulfillmentTaskReadyEvent(
                eventId, fulfillmentNo, LocalDateTime.now());
        try {
            mapper.insert(FulfillmentOutbox.pending(
                    eventId, fulfillmentNo, TASK_READY_EVENT, json.writeValueAsString(event)));
        } catch (JacksonException exception) {
            throw new IllegalStateException("履约任务触发事件序列化失败", exception);
        }
    }
}
