package com.dongqh.lotteryservice.activitystatus;

import com.dongqh.lotteryservice.mapper.activitystatus.ActivityStatusMapper;
import com.dongqh.lotteryservice.service.activitystatus.ActivityStatusRefreshResult;
import com.dongqh.lotteryservice.service.activitystatus.ActivityStatusService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActivityStatusServiceTests {

    @Test
    void promotesThenEndsActivitiesAndReturnsBothAffectedRowCounts() {
        ActivityStatusMapper mapper = mock(ActivityStatusMapper.class);
        when(mapper.promoteScheduledToRunning()).thenReturn(2);
        when(mapper.finishExpiredActivities()).thenReturn(3);

        ActivityStatusRefreshResult result = new ActivityStatusService(mapper).refreshStatuses();

        assertThat(result).isEqualTo(new ActivityStatusRefreshResult(2, 3));
        InOrder calls = inOrder(mapper);
        calls.verify(mapper).promoteScheduledToRunning();
        calls.verify(mapper).finishExpiredActivities();
        calls.verifyNoMoreInteractions();
    }
}
