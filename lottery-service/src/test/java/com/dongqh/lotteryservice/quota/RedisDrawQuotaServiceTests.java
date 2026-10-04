package com.dongqh.lotteryservice.quota;

import com.dongqh.lotteryservice.config.LotteryProperties;
import com.dongqh.lotteryservice.domain.LotteryDomainException;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisDrawQuotaServiceTests {

    private static final LocalDate DRAW_DATE = LocalDate.of(2026, 9, 27);
    private static final List<String> KEYS = List.of(
            "draw:quota:42:101:20260927",
            "draw:reservation:request-1",
            "draw:reservation:timeouts");

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final LotteryProperties properties = new LotteryProperties(
            ZoneId.of("Asia/Shanghai"), Duration.ofSeconds(3), Duration.ofMinutes(2),
            Duration.ofSeconds(30), Duration.ofSeconds(60), 100,
            Duration.ofHours(72), Duration.ofSeconds(5), 100);
    private final RedisDrawQuotaService service = new RedisDrawQuotaService(
            redis, properties, Clock.fixed(Instant.parse("2026-09-27T02:00:00Z"), ZoneId.of("UTC")));

    @Test
    void reservesWithTheStableKeysAndReturnsTheScriptSnapshot() {
        when(redis.execute(any(DefaultRedisScript.class), eq(KEYS),
                eq("request-1"), eq("42"), eq("101"), eq("1"), eq("5"),
                eq("20260927"), any(), any(), any()))
                .thenReturn(List.of(1L, "RESERVED", "20260927", "1"));

        QuotaReservationResult result = service.reserve(
                new QuotaReservationRequest("request-1", 42, 101, 1, 5, DRAW_DATE));

        assertThat(result).isEqualTo(new QuotaReservationResult(
                "request-1", ReservationStatus.RESERVED, DRAW_DATE, 1, false));
    }

    @Test
    void mapsInsufficientDailyQuotaToTheExistingDomainError() {
        when(redis.execute(any(DefaultRedisScript.class), eq(KEYS),
                eq("request-1"), eq("42"), eq("101"), eq("1"), eq("5"),
                eq("20260927"), any(), any(), any()))
                .thenReturn(List.of(2L, "RESERVED", "20260927", "1"));

        assertThatThrownBy(() -> service.reserve(
                new QuotaReservationRequest("request-1", 42, 101, 1, 5, DRAW_DATE)))
                .isInstanceOfSatisfying(LotteryDomainException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.DAILY_QUOTA_EXCEEDED));
    }

    @Test
    void returnsDuplicateReservationWithoutReplacingItsStoredPolicyDateOrCount() {
        when(redis.execute(any(DefaultRedisScript.class), eq(KEYS),
                eq("request-1"), eq("42"), eq("101"), eq("1"), eq("99"),
                eq("20260927"), any(), any(), any()))
                .thenReturn(List.of(0L, "CONFIRMED", "20260926", "1"));

        QuotaReservationResult result = service.reserve(
                new QuotaReservationRequest("request-1", 42, 101, 1, 99, DRAW_DATE));

        assertThat(result.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.drawDate()).isEqualTo(DRAW_DATE.minusDays(1));
        assertThat(result.duplicate()).isTrue();
    }
}
