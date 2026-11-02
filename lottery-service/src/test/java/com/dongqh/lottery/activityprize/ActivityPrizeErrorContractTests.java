package com.dongqh.lottery.activityprize;

import com.dongqh.lottery.exception.activityprize.ActivityPrizeErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityPrizeErrorContractTests {

    @Test
    void preservesLegacyHttpStatusesForActivityPrizeErrors() {
        assertThat(ActivityPrizeErrorCode.ACTIVITY_NOT_FOUND.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ActivityPrizeErrorCode.ACTIVITY_STATE_CONFLICT.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ActivityPrizeErrorCode.ACTIVITY_PRIZE_NOT_FOUND.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ActivityPrizeErrorCode.ACTIVITY_PRIZE_DUPLICATE.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ActivityPrizeErrorCode.ACTIVITY_HAS_DISABLED_PRIZE.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ActivityPrizeErrorCode.ACTIVITY_STOCK_BELOW_CONSUMED.httpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
