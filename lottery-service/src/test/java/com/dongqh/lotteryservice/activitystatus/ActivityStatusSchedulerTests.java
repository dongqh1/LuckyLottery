package com.dongqh.lotteryservice.activitystatus;

import com.dongqh.lotteryservice.scheduler.activitystatus.ActivityStatusScheduler;
import com.dongqh.lotteryservice.service.activitystatus.ActivityStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ActivityStatusSchedulerTests {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(ActivityStatusService.class, () -> mock(ActivityStatusService.class))
            .withUserConfiguration(ActivityStatusScheduler.class);

    @Test
    void schedulerIsAbsentUnlessLotterySpecificFlagIsTrue() {
        context.run(result -> assertThat(result).doesNotHaveBean(ActivityStatusScheduler.class));
        context.withPropertyValues("luckyhub.lottery.activity-status.enabled=false")
                .run(result -> assertThat(result).doesNotHaveBean(ActivityStatusScheduler.class));
        context.withPropertyValues("luckyhub.lottery.activity-status.enabled=true")
                .run(result -> assertThat(result).hasSingleBean(ActivityStatusScheduler.class));
    }

    @Test
    void delegatesRefreshAndRetainsLegacyIntervalDefaults() throws NoSuchMethodException {
        ActivityStatusService service = mock(ActivityStatusService.class);
        new ActivityStatusScheduler(service).refreshActivityStatuses();
        verify(service).refreshStatuses();

        Scheduled schedule = ActivityStatusScheduler.class.getMethod("refreshActivityStatuses")
                .getAnnotation(Scheduled.class);
        assertThat(schedule.fixedDelayString()).isEqualTo("${luckyhub.lottery.activity-status.refresh-interval:30000}");
        assertThat(schedule.initialDelayString()).isEqualTo("${luckyhub.lottery.activity-status.initial-delay:0}");
    }
}
