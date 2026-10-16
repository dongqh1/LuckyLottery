package com.dongqh.luckyhub.domain.vo.lottery;

import com.dongqh.luckyhub.enums.activity.ActivityStatus;
import java.time.LocalDateTime;

public record LotteryActivityView(Long id, String activityName, String description, ActivityStatus status,
                                  LocalDateTime startTime, LocalDateTime endTime) {
}
