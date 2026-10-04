package com.dongqh.lotteryservice.outbox.scheduler;

import com.dongqh.lotteryservice.outbox.service.OutboxRelayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "luckyhub.lottery.outbox", name = "enabled", havingValue = "true")
public class OutboxRelayScheduler {
    private final OutboxRelayService relayService;

    public OutboxRelayScheduler(OutboxRelayService relayService) { this.relayService = relayService; }

    @Scheduled(fixedDelayString = "${luckyhub.lottery.outbox-interval:5s}",
            initialDelayString = "${luckyhub.lottery.outbox-initial-delay:60s}")
    public void relayOutbox() { relayService.relayBatch(); }
}
