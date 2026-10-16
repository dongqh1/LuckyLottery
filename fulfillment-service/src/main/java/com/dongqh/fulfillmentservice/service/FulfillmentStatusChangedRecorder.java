package com.dongqh.fulfillmentservice.service;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.enums.FulfillmentType;
import com.dongqh.fulfillmentservice.domain.model.FulfillmentTaskView;
import com.dongqh.fulfillmentservice.service.port.FulfillmentStatusChangedOutbox;
import com.dongqh.hmapi.dto.FulfillmentStatusChangedEventV1;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FulfillmentStatusChangedRecorder {
    private static final String LOTTERY_SOURCE = "LOTTERY_BENEFIT";

    private final FulfillmentStatusChangedOutbox outbox;
    private final Clock clock;

    public FulfillmentStatusChangedRecorder(FulfillmentStatusChangedOutbox outbox, Clock fulfillmentClock) {
        this.outbox = outbox;
        this.clock = fulfillmentClock;
    }

    public void recordLotteryAssetTask(FulfillmentTaskView task) {
        if (task == null || !LOTTERY_SOURCE.equals(task.sourceType())
                || task.fulfillmentType() == FulfillmentType.LOGISTICS
                || (task.status() != FulfillmentStatus.SUCCEEDED
                && task.status() != FulfillmentStatus.QUARANTINED
                && task.status() != FulfillmentStatus.TERMINATED)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        outbox.append(new FulfillmentStatusChangedEventV1(UUID.randomUUID().toString(),
                task.fulfillmentNo(), null, task.sourceType(), task.sourceId(), task.status().name(), now));
    }
}
