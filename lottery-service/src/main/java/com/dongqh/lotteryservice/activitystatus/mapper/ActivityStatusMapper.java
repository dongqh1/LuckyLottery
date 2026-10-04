package com.dongqh.lotteryservice.activitystatus.mapper;

import org.apache.ibatis.annotations.Update;

public interface ActivityStatusMapper {

    @Update("""
            UPDATE marketing_activity
            SET status = 'RUNNING'
            WHERE status = 'SCHEDULED'
              AND start_time <= NOW(3)
              AND end_time > NOW(3)
            """)
    int promoteScheduledToRunning();

    @Update("""
            UPDATE marketing_activity
            SET status = 'ENDED'
            WHERE status IN ('SCHEDULED', 'RUNNING')
              AND end_time <= NOW(3)
            """)
    int finishExpiredActivities();
}
