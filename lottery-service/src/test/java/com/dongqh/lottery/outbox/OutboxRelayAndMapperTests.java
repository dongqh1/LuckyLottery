package com.dongqh.lottery.outbox;

import com.dongqh.lottery.outbox.event.DrawConfirmedEvent;
import com.dongqh.lottery.outbox.event.DrawEventEnvelope;
import com.dongqh.lottery.outbox.event.DrawEventType;
import com.dongqh.lottery.outbox.mapper.MessageOutboxMapper;
import com.dongqh.lottery.outbox.model.MessageOutbox;
import com.dongqh.lottery.outbox.publisher.DrawEventPublisher;
import com.dongqh.lottery.outbox.service.OutboxRelayService;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayAndMapperTests {
    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
    private final MessageOutboxMapper mapper = mock(MessageOutboxMapper.class);
    private final DrawEventPublisher publisher = mock(DrawEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 16, 12, 0);

    @Test
    void claimsWithLeaseThenPublishesAndMarksSentWithSameToken() throws Exception {
        DrawEventEnvelope event = event();
        MessageOutbox row = row(json.writeValueAsString(event), 0);
        when(mapper.selectRelayCandidates(now, 10)).thenReturn(List.of(row));
        when(mapper.claimForRelay(eq(100L), anyString(), eq(now), eq(now.plusSeconds(30)))).thenReturn(1);
        when(mapper.markSent(eq(100L), anyString(), eq(now))).thenReturn(1);

        assertThat(relay().relayBatch()).isOne();

        ArgumentCaptor<String> claimedToken = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sentToken = ArgumentCaptor.forClass(String.class);
        verify(mapper).claimForRelay(eq(100L), claimedToken.capture(), eq(now), eq(now.plusSeconds(30)));
        verify(publisher).publish(event);
        verify(mapper).markSent(eq(100L), sentToken.capture(), eq(now));
        assertThat(sentToken.getValue()).isEqualTo(claimedToken.getValue());
        verify(mapper, never()).markFailed(anyLong(), anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void skipsLostClaimAndNeverPublishes() throws Exception {
        when(mapper.selectRelayCandidates(now, 10)).thenReturn(List.of(row(json.writeValueAsString(event()), 0)));
        assertThat(relay().relayBatch()).isZero();
        verify(publisher, never()).publish(any());
    }

    @Test
    void publisherFailureUsesBoundedExponentialBackoffAndClearsNoSentState() throws Exception {
        DrawEventEnvelope event = event();
        when(mapper.selectRelayCandidates(now, 10)).thenReturn(List.of(row(json.writeValueAsString(event), 2)));
        when(mapper.claimForRelay(eq(100L), anyString(), eq(now), any(LocalDateTime.class))).thenReturn(1);
        doThrow(new IllegalStateException("broker NACK")).when(publisher).publish(event);

        assertThat(relay().relayBatch()).isZero();

        verify(mapper).markFailed(eq(100L), anyString(), eq("broker NACK"), eq(now.plusSeconds(8)));
        verify(mapper, never()).markSent(anyLong(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void malformedPayloadIsFailedWithoutPublishing() {
        when(mapper.selectRelayCandidates(now, 10)).thenReturn(List.of(row("{}", 0)));
        when(mapper.claimForRelay(eq(100L), anyString(), eq(now), any(LocalDateTime.class))).thenReturn(1);
        assertThat(relay().relayBatch()).isZero();
        verify(publisher, never()).publish(any());
        verify(mapper).markFailed(eq(100L), anyString(), anyString(), eq(now.plusSeconds(2)));
    }

    @Test
    void relayRequiresPositiveBatchSizeAndLeaseDuration() {
        assertThatThrownBy(() -> new OutboxRelayService(mapper, publisher, json,
                0, Duration.ofSeconds(30), clock))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("batchSize");
        assertThatThrownBy(() -> new OutboxRelayService(mapper, publisher, json,
                10, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("leaseDuration");
        assertThatThrownBy(() -> new OutboxRelayService(mapper, publisher, json,
                10, Duration.ofSeconds(-1), clock))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("leaseDuration");
    }

    @Test
    void mapperSqlKeepsRetryLeaseAndTokenGuards() throws Exception {
        String select = String.join(" ", MessageOutboxMapper.class
                .getMethod("selectRelayCandidates", LocalDateTime.class, int.class)
                .getAnnotation(Select.class).value());
        String claim = String.join(" ", MessageOutboxMapper.class
                .getMethod("claimForRelay", long.class, String.class, LocalDateTime.class, LocalDateTime.class)
                .getAnnotation(Update.class).value());
        String sent = String.join(" ", MessageOutboxMapper.class
                .getMethod("markSent", long.class, String.class, LocalDateTime.class)
                .getAnnotation(Update.class).value());
        String failed = String.join(" ", MessageOutboxMapper.class
                .getMethod("markFailed", long.class, String.class, String.class, LocalDateTime.class)
                .getAnnotation(Update.class).value());
        assertThat(select).contains("status = 'PENDING'", "status = 'FAILED'", "status = 'PROCESSING'",
                "next_retry_at <= #{now}", "ORDER BY id", "LIMIT #{limit}");
        assertThat(claim).contains("claim_token = #{claimToken}", "next_retry_at = #{leaseUntil}",
                "status = 'PROCESSING'", "next_retry_at <= #{now}");
        assertThat(sent).contains("status = 'SENT'", "claim_token = #{claimToken}",
                "claim_token = NULL", "next_retry_at = NULL");
        assertThat(failed).contains("status = 'FAILED'", "retry_count = retry_count + 1",
                "claim_token = #{claimToken}", "next_retry_at = #{nextRetryAt}");
    }

    private OutboxRelayService relay() {
        return new OutboxRelayService(mapper, publisher, json, 10, Duration.ofSeconds(30), clock);
    }

    private DrawEventEnvelope event() {
        return DrawEventEnvelope.create(DrawEventType.DRAW_CONFIRMED, "relay-" + UUID.randomUUID(),
                1L, 2L, 3L, now, new DrawConfirmedEvent(1, LocalDate.of(2026, 9, 16)), json);
    }

    private MessageOutbox row(String payload, int retryCount) {
        MessageOutbox row = new MessageOutbox();
        row.setId(100L);
        row.setPayload(payload);
        row.setRetryCount(retryCount);
        return row;
    }
}
