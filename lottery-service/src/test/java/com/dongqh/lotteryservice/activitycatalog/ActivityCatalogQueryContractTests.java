package com.dongqh.lotteryservice.activitycatalog;

import com.dongqh.lotteryservice.domain.vo.activitycatalog.ActivityCatalogView;
import com.dongqh.lotteryservice.domain.vo.activitycatalog.ActivityPrizeCatalogView;
import com.dongqh.lotteryservice.config.activityprize.ActivityPrizeMapperConfiguration;
import com.dongqh.lotteryservice.mapper.activityprize.ActivityCatalogQueryMapper;
import com.dongqh.lotteryservice.mapper.activityprize.ActivityMapper;
import com.dongqh.lotteryservice.enums.activityprize.ActivityStatus;
import com.dongqh.lotteryservice.enums.activityprize.PrizeType;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.http.HttpStatus;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityCatalogQueryContractTests {

    @Test
    void mapperSqlPreservesPublicActivityQueryPredicatesOrderingAndLimit() throws Exception {
        String sql = sql("listActivities");

        assertThat(sql).contains("FROM marketing_activity", "status = 'RUNNING'", "start_time <= #{now}",
                "end_time >= #{now}", "ORDER BY end_time ASC, id ASC", "LIMIT 100");
    }

    @Test
    void mapperSqlGetsAnyExistingActivityByIdAndReturnsTheSameProjection() throws Exception {
        String sql = sql("findActivityById");

        assertThat(sql).contains("id", "activity_name", "description", "status", "start_time", "end_time",
                "FROM marketing_activity", "WHERE id = #{activityId}");
        assertThat(sql.toUpperCase()).doesNotContain("STATUS = 'RUNNING'");
    }

    @Test
    void prizeSqlPreservesVisiblePrizeContractWithoutLeakingStockOrWeight() throws Exception {
        String sql = sql("listVisiblePrizes");
        String projection = sql.substring(0, sql.toUpperCase().indexOf("FROM"));

        assertThat(sql).contains("FROM marketing_activity_prize ap", "JOIN marketing_prize p ON p.id = ap.prize_id",
                "LEFT JOIN reward_definition rd ON rd.id = p.reward_definition_id",
                "CASE WHEN rd.reward_type = 'PRODUCT' THEN rd.target_id END AS hmall_item_id",
                "ap.activity_id = #{activityId}", "ap.remaining_stock > 0", "p.status = 1",
                "ORDER BY ap.sort_order ASC, ap.id ASC", "LIMIT 100");
        assertThat(projection.toLowerCase()).doesNotContain("weight", "stock");
    }

    @Test
    void activityPrizeMapperScanStaysNarrowToTheActivityPrizePackage() {
        MapperScan scan = ActivityPrizeMapperConfiguration.class.getAnnotation(MapperScan.class);

        assertThat(scan).isNotNull();
        assertThat(scan.basePackageClasses()).containsExactly(ActivityMapper.class);
        assertThat(ActivityCatalogQueryMapper.class.getPackageName())
                .isEqualTo(ActivityMapper.class.getPackageName());
    }

    @Test
    void viewsPreserveLegacyFieldNamesTypesAndEnumSpellings() {
        assertRecordComponents(ActivityCatalogView.class, new String[]{"id", "activityName", "description", "status",
                "startTime", "endTime"},
                new Class<?>[]{Long.class, String.class, String.class, ActivityStatus.class,
                        LocalDateTime.class, LocalDateTime.class});
        assertRecordComponents(ActivityPrizeCatalogView.class,
                new String[]{"prizeId", "prizeName", "prizeType", "imageUrl", "sortOrder", "hmallItemId"},
                new Class<?>[]{Long.class, String.class, PrizeType.class, String.class, Integer.class, Long.class});
        assertThat(Arrays.stream(ActivityStatus.values()).map(Enum::name).toList())
                .containsExactly("DRAFT", "SCHEDULED", "RUNNING", "ENDED", "DISABLED");
        assertThat(Arrays.stream(PrizeType.values()).map(Enum::name).toList())
                .containsExactly("COUPON", "POINTS", "MEMBERSHIP", "PHYSICAL", "DRAW_CHANCE");
    }

    @Test
    void missingActivityErrorKeepsExistingLotteryNotFoundCodeAndHttpStatus() {
        assertThat(com.dongqh.lotteryservice.enums.LotteryErrorCode.ACTIVITY_NOT_FOUND.code()).isEqualTo(43001);
        assertThat(com.dongqh.lotteryservice.enums.LotteryErrorCode.ACTIVITY_NOT_FOUND.httpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String sql(String methodName) throws Exception {
        Select select = Arrays.stream(ActivityCatalogQueryMapper.class.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst().orElseThrow().getAnnotation(Select.class);
        return String.join(" ", select.value()).replaceAll("\\s+", " ").trim();
    }

    private void assertRecordComponents(Class<?> recordType, String[] names, Class<?>[] types) {
        RecordComponent[] components = recordType.getRecordComponents();
        assertThat(components).hasSize(names.length);
        for (int index = 0; index < names.length; index++) {
            assertThat(components[index].getName()).isEqualTo(names[index]);
            assertThat(components[index].getType()).isEqualTo(types[index]);
        }
    }
}


