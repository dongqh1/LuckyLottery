package com.dongqh.lottery.activitystatus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class LotteryApplicationYamlTests {

    @Test
    void keepsLotterySettingsAndLegacyDataRedisSettingsInOneParseableMapping() throws IOException {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("lottery", new ClassPathResource("application.yml")).get(0);

        assertThat(properties.getProperty("luckyhub.lottery.zone-id"))
                .isEqualTo("${LOTTERY_ZONE_ID:Asia/Shanghai}");
        assertThat(properties.getProperty("luckyhub.lottery.outbox-batch-size"))
                .isEqualTo("${LOTTERY_OUTBOX_BATCH_SIZE:100}");
        assertThat(properties.getProperty("luckyhub.data.redis.host"))
                .isEqualTo("${LOTTERY_REDIS_HOST}");
        assertThat(properties.getProperty("luckyhub.data.redis.password"))
                .isEqualTo("${LOTTERY_REDIS_PASSWORD:}");
        assertThat(properties.getProperty("spring.data.redis.host"))
                .isEqualTo("${LOTTERY_REDIS_HOST}");
    }
}
