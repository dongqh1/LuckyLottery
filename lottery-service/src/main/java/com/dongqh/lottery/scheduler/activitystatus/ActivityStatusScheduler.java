package com.dongqh.lottery.scheduler.activitystatus;

import com.dongqh.lottery.service.activitystatus.ActivityStatusService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "luckyhub.lottery.activity-status", name = "enabled", havingValue = "true")
public class ActivityStatusScheduler {

    private final ActivityStatusService service;

    public ActivityStatusScheduler(ActivityStatusService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${luckyhub.lottery.activity-status.refresh-interval:30000}",
            initialDelayString = "${luckyhub.lottery.activity-status.initial-delay:0}"
    )
    public void refreshActivityStatuses() {
        service.refreshStatuses();
    }
}
