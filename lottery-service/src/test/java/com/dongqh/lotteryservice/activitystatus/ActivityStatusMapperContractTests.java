package com.dongqh.lotteryservice.activitystatus;

import com.dongqh.lotteryservice.mapper.activitystatus.ActivityStatusMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityStatusMapperContractTests {

    @Test
    void transitionsUseLotteryActivityTableAndDatabaseMillisecondClock() throws NoSuchMethodException {
        String promote = String.join(" ", ActivityStatusMapper.class
                .getMethod("promoteScheduledToRunning").getAnnotation(Update.class).value());
        String finish = String.join(" ", ActivityStatusMapper.class
                .getMethod("finishExpiredActivities").getAnnotation(Update.class).value());

        assertThat(promote).contains("UPDATE marketing_activity", "status = 'RUNNING'",
                "status = 'SCHEDULED'", "start_time <= NOW(3)", "end_time > NOW(3)");
        assertThat(finish).contains("UPDATE marketing_activity", "status = 'ENDED'",
                "status IN ('SCHEDULED', 'RUNNING')", "end_time <= NOW(3)");
    }
}
