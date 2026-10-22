package com.dongqh.lotteryservice.outbox;

import com.dongqh.lotteryservice.outbox.event.DrawConfirmedEvent;
import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import com.dongqh.lotteryservice.outbox.event.DrawEventType;
import com.dongqh.lotteryservice.outbox.event.DrawReleaseRequestedEvent;
import com.dongqh.lotteryservice.outbox.event.PrizeFulfillmentRequestedEvent;
import com.dongqh.lotteryservice.outbox.mapper.MessageOutboxMapper;
import com.dongqh.lotteryservice.outbox.model.MessageOutbox;
import com.dongqh.lotteryservice.outbox.model.OutboxStatus;
import com.dongqh.lotteryservice.outbox.service.OutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventAndAppendTests {
    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();

    @Test
    void fulfillmentEventContainsStableIdsAndIgnoresLegacyRewardFields() throws Exception {
        assertThat(Arrays.stream(PrizeFulfillmentRequestedEvent.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .containsExactly("benefitId", "drawRecordId", "prizeId");

        var legacyPayload = json.readTree("""
                {"benefitId":51,"drawRecordId":52,"prizeId":53,"legacyDisplay":"old value",
                 "legacyRewardConfig":{"code":"old-config"}}
                """);
        var envelope = new DrawEventEnvelope(UUID.randomUUID(), DrawEventType.PRIZE_FULFILLMENT_REQUESTED, 1,
                "request-legacy", 21L, 31L, 41L, LocalDateTime.of(2026, 7, 31, 12, 30), legacyPayload);

        PrizeFulfillmentRequestedEvent decoded = envelope.payloadAs(PrizeFulfillmentRequestedEvent.class, json);
        assertThat(decoded.benefitId()).isEqualTo(51L);
        assertThat(decoded.drawRecordId()).isEqualTo(52L);
        assertThat(decoded.prizeId()).isEqualTo(53L);
    }

    @Test
    void envelopeKeepsUuidVersionJsonAndImmutablePayload() throws Exception {
        ObjectNode payload = json.createObjectNode().put("drawCount", 1);
        DrawEventEnvelope event = new DrawEventEnvelope(UUID.randomUUID(), DrawEventType.DRAW_CONFIRMED, 1,
                "request-1", 21L, 31L, 41L, LocalDateTime.of(2026, 7, 31, 12, 30), payload);
        payload.put("drawCount", 10);
        ((ObjectNode) event.payload()).put("drawCount", 10);
        assertThat(event.payload().get("drawCount").asInt()).isOne();
        assertThat(event.eventId().version()).isEqualTo(4);
        assertThat(event.eventVersion()).isEqualTo(1);
        String serialized = json.writeValueAsString(event);
        assertThat(serialized).contains("\"eventType\":\"DRAW_CONFIRMED\"", "\"eventVersion\":1");
        assertThat(json.readValue(serialized, DrawEventEnvelope.class)).isEqualTo(event);
    }

    @Test
    void payloadTypesRoundTripAndFulfillmentMessageContainsOnlyStableIds() throws Exception {
        assertRoundTrip(DrawEventType.DRAW_CONFIRMED,
                new DrawConfirmedEvent(10, LocalDate.of(2026, 7, 31)), DrawConfirmedEvent.class);
        assertRoundTrip(DrawEventType.DRAW_RELEASE_REQUESTED,
                new DrawReleaseRequestedEvent(1, LocalDate.of(2026, 7, 31), "DRAW_TRANSACTION_FAILED"),
                DrawReleaseRequestedEvent.class);
        var fulfillment = new PrizeFulfillmentRequestedEvent(51L, 52L, 53L);
        assertRoundTrip(DrawEventType.PRIZE_FULFILLMENT_REQUESTED, fulfillment,
                PrizeFulfillmentRequestedEvent.class);
        assertThatThrownBy(() -> new PrizeFulfillmentRequestedEvent(null, 52L, 53L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appendPersistsPendingEnvelopeWithMandatoryTransaction() throws Exception {
        MessageOutboxMapper mapper = mock(MessageOutboxMapper.class);
        DrawEventEnvelope event = DrawEventEnvelope.create(DrawEventType.DRAW_CONFIRMED,
                "request-2", 21L, 31L, 41L, LocalDateTime.of(2026, 7, 31, 12, 30),
                new DrawConfirmedEvent(1, LocalDate.of(2026, 7, 31)), json);
        new OutboxService(mapper, json).append(event);

        ArgumentCaptor<MessageOutbox> inserted = ArgumentCaptor.forClass(MessageOutbox.class);
        verify(mapper).insert(inserted.capture());
        MessageOutbox row = inserted.getValue();
        assertThat(row.getEventId()).isEqualTo(event.eventId().toString());
        assertThat(row.getEventType()).isEqualTo("DRAW_CONFIRMED");
        assertThat(row.getEventVersion()).isEqualTo(1);
        assertThat(row.getAggregateType()).isEqualTo("LOTTERY_DRAW_ORDER");
        assertThat(row.getAggregateId()).isEqualTo("41");
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getRetryCount()).isZero();
        assertThat(json.readValue(row.getPayload(), DrawEventEnvelope.class)).isEqualTo(event);
        assertThat(OutboxService.class.getMethod("append", DrawEventEnvelope.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }

    private <T> void assertRoundTrip(DrawEventType type, T payload, Class<T> payloadType) throws Exception {
        DrawEventEnvelope event = DrawEventEnvelope.create(type, "request-" + type,
                21L, 31L, 41L, LocalDateTime.of(2026, 7, 31, 12, 30), payload, json);
        assertThat(json.readValue(json.writeValueAsString(event), DrawEventEnvelope.class)
                .payloadAs(payloadType, json)).isEqualTo(payload);
    }
}
