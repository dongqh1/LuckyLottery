package com.dongqh.payservice.messaging;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.payservice.mapper.PayMessageOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PayOutboxService {
    private final PayMessageOutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public PayOutboxService(PayMessageOutboxMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(PaySuccessMessage event) {
        PayMessageOutbox row = new PayMessageOutbox();
        row.setEventId(event.eventId());
        row.setEventType("PAY_SUCCESS");
        row.setAggregateId(event.orderId().toString());
        try {
            row.setPayload(objectMapper.writeValueAsString(event));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Payment-success event cannot be serialized", exception);
        }
        row.setStatus(PayOutboxStatus.PENDING);
        row.setRetryCount(0);
        row.setCreatedAt(event.paidAt());
        mapper.insert(row);
    }
}
