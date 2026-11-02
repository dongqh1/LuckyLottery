package com.dongqh.fulfillment.config;

import com.dongqh.fulfillment.persistence.FulfillmentOutboxRepository;
import com.dongqh.fulfillment.service.FulfillmentOutboxRelayService;
import com.dongqh.fulfillment.service.port.FulfillmentStatusChangedPublisher;
import com.dongqh.fulfillment.service.port.FulfillmentTaskReadyPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "luckyhub.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FulfillmentOutboxRelayConfiguration {

    @Bean
    public FulfillmentOutboxRelayService fulfillmentOutboxRelayService(
            FulfillmentOutboxRepository repository,
            FulfillmentTaskReadyPublisher taskReadyPublisher,
            FulfillmentStatusChangedPublisher statusChangedPublisher,
            ObjectMapper json,
            Clock fulfillmentClock,
            @Value("${luckyhub.fulfillment.outbox-batch-size:100}") int batchSize,
            @Value("${luckyhub.messaging.outbox-lease:30s}") Duration leaseDuration
    ) {
        return new FulfillmentOutboxRelayService(repository, taskReadyPublisher, statusChangedPublisher,
                json, batchSize, leaseDuration, fulfillmentClock);
    }
}
