package com.dongqh.luckyhub.activity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.domain.po.activity.MarketingActivity;
import com.dongqh.luckyhub.domain.po.activity.MarketingActivityPrize;
import com.dongqh.luckyhub.enums.activity.ActivityStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityPersistenceContractTests {

    @Test
    void definesPersistedStatusesInLifecycleOrder() {
        assertThat(ActivityStatus.values()).containsExactly(
                ActivityStatus.DRAFT,
                ActivityStatus.SCHEDULED,
                ActivityStatus.RUNNING,
                ActivityStatus.ENDED,
                ActivityStatus.DISABLED
        );
    }

    @Test
    void mapsExistingActivityTables() {
        assertThat(MarketingActivity.class.getAnnotation(TableName.class).value())
                .isEqualTo("marketing_activity");
        assertThat(MarketingActivityPrize.class.getAnnotation(TableName.class).value())
                .isEqualTo("marketing_activity_prize");
    }
}
