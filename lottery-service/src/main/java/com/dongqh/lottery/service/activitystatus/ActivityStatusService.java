package com.dongqh.lottery.service.activitystatus;

import com.dongqh.lottery.mapper.activitystatus.ActivityStatusMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityStatusService {

    private final ActivityStatusMapper mapper;

    public ActivityStatusService(ActivityStatusMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public ActivityStatusRefreshResult refreshStatuses() {
        int runningCount = mapper.promoteScheduledToRunning();
        int endedCount = mapper.finishExpiredActivities();
        return new ActivityStatusRefreshResult(runningCount, endedCount);
    }
}
