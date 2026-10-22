package com.dongqh.tradeservice.messaging;

import com.dongqh.hmapi.dto.OrderCreatedMessage;
import com.dongqh.hmapi.dto.OrderPaidMessage;
import com.dongqh.tradeservice.mapper.TradeMessageOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TradeOutboxService {
    private final TradeMessageOutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public TradeOutboxService(TradeMessageOutboxMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OrderCreatedMessage event) {
        append(event.eventId().toString(), "ORDER_CREATED", event.orderId().toString(), event, event.occurredAt());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OrderPaidMessage event) {
        append(event.eventId().toString(), "ORDER_PAID", event.orderId().toString(), event, event.paidAt());
    }

    private void append(String eventId, String eventType, String aggregateId, Object event,
                        java.time.LocalDateTime occurredAt) {
        TradeMessageOutbox row = new TradeMessageOutbox();
        row.setEventId(eventId);
        row.setEventType(eventType);
        row.setAggregateId(aggregateId);
        try {
            row.setPayload(objectMapper.writeValueAsString(event));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Trade event cannot be serialized", exception);
        }
        row.setStatus(TradeOutboxStatus.PENDING);
        row.setRetryCount(0);
        row.setCreatedAt(occurredAt);
        mapper.insert(row);
    }
}
