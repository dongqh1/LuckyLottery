package com.dongqh.lotteryservice.outbox.service;

import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import com.dongqh.lotteryservice.outbox.mapper.MessageOutboxMapper;
import com.dongqh.lotteryservice.outbox.model.MessageOutbox;
import com.dongqh.lotteryservice.outbox.model.OutboxStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Service
public class OutboxService {
    private static final String DRAW_ORDER_AGGREGATE = "LOTTERY_DRAW_ORDER";
    private final MessageOutboxMapper mapper;
    private final ObjectMapper json;

    public OutboxService(MessageOutboxMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DrawEventEnvelope event) {
        Objects.requireNonNull(event, "event must not be null");
        MessageOutbox outbox = new MessageOutbox();
        outbox.setEventId(event.eventId().toString());
        outbox.setEventType(event.eventType().name());
        outbox.setEventVersion(event.eventVersion());
        outbox.setAggregateType(DRAW_ORDER_AGGREGATE);
        outbox.setAggregateId(event.orderId().toString());
        outbox.setPayload(serialize(event));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setRetryCount(0);
        mapper.insert(outbox);
    }

    private String serialize(DrawEventEnvelope event) {
        try {
            return json.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Draw event cannot be serialized", exception);
        }
    }
}
