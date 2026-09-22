package com.dongqh.tradeservice.messaging;

import com.dongqh.hmapi.dto.OrderCreatedMessage;
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
        TradeMessageOutbox row = new TradeMessageOutbox();
        row.setEventId(event.eventId().toString());
        row.setEventType("ORDER_CREATED");
        row.setAggregateId(event.orderId().toString());
        try {
            row.setPayload(objectMapper.writeValueAsString(event));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Order-created event cannot be serialized", exception);
        }
        row.setStatus(TradeOutboxStatus.PENDING);
        row.setRetryCount(0);
        row.setCreatedAt(event.occurredAt());
        mapper.insert(row);
    }
}
