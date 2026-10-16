package com.dongqh.luckyhub.config.lottery;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "seata.enabled=false",
        "luckyhub.messaging.enabled=false"
})
class LotteryConfigurationTests {

    @Autowired
    private LotteryProperties properties;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private Environment environment;

    @Test
    void bindsSafeLotteryDefaults() {
        assertThat(properties.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(properties.lockWait()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.processingTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.reconcileInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.reconcileInitialDelay()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.reconcileBatchSize()).isEqualTo(100);
        assertThat(properties.outboxInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.outboxBatchSize()).isEqualTo(100);
        assertThat(redissonClient).isNotNull();
    }

    @Test
    void exposesRabbitMqReliabilitySettingsWithoutRedisStreamTransportSettings() {
        assertThat(environment.getProperty("spring.rabbitmq.virtual-host")).isEqualTo("/dongqh");
        assertThat(environment.getProperty("spring.rabbitmq.publisher-confirm-type"))
                .isEqualTo("correlated");
        assertThat(environment.getProperty("spring.rabbitmq.publisher-returns", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("spring.rabbitmq.template.mandatory", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("luckyhub.messaging.publisher-confirm-timeout", Duration.class))
                .isEqualTo(Duration.ofSeconds(5));
        assertThat(environment.getProperty("luckyhub.messaging.provider")).isNull();
        assertThat(environment.getProperty("luckyhub.messaging.lottery-stream")).isNull();
        assertThat(environment.getProperty("luckyhub.messaging.lottery-group")).isNull();
    }

    @Test
    void rejectsUnsafeMessagingAndOutboxSettings() {
        assertThatThrownBy(() -> new MessagingProperties(
                true, " ", Duration.ofSeconds(5), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessagingProperties(
                true, "consumer", Duration.ZERO, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessagingProperties(
                true, "consumer", Duration.ofSeconds(5), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LotteryProperties(ZoneId.of("Asia/Shanghai"), Duration.ofSeconds(1),
                Duration.ofMinutes(2), Duration.ofSeconds(30), Duration.ofSeconds(1), 100,
                Duration.ofSeconds(5), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> propertiesWithReconciliation(Duration.ZERO, Duration.ZERO, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> propertiesWithReconciliation(Duration.ofSeconds(1),
                Duration.ofSeconds(-1), 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> propertiesWithReconciliation(Duration.ofSeconds(1),
                Duration.ZERO, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LotteryProperties(ZoneId.of("Asia/Shanghai"), Duration.ofSeconds(1),
                Duration.ZERO, Duration.ofSeconds(30), Duration.ZERO, 100,
                Duration.ofSeconds(5), 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private LotteryProperties propertiesWithReconciliation(
            Duration interval, Duration initialDelay, int batchSize) {
        return new LotteryProperties(ZoneId.of("Asia/Shanghai"), Duration.ofSeconds(1),
                Duration.ofMinutes(2), interval, initialDelay, batchSize,
                Duration.ofSeconds(5), 100);
    }
}


