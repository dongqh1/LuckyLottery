package com.dongqh.lotteryservice.draworder;

import com.dongqh.lotteryservice.config.draworder.DrawOrderMapperConfiguration;
import com.dongqh.lotteryservice.mapper.draworder.DrawOrderMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DrawOrderMapperContractTests {

    @Test
    void insertRemainsIdempotentAndProcessingOnly() throws Exception {
        String sql = sql("insertProcessingIfAbsent", Insert.class);

        assertThat(sql).contains("INSERT INTO lottery_draw_order", "request_id", "user_id", "activity_id",
                "draw_count", "draw_date", "'PROCESSING'", "ON DUPLICATE KEY UPDATE id = id");
    }

    @Test
    void duplicateReadsRetainRequestIdPredicateAndLockingCurrentRead() throws Exception {
        assertThat(sql("selectByRequestId", Select.class))
                .isEqualTo("SELECT * FROM lottery_draw_order WHERE request_id = #{requestId}");
        assertThat(sql("selectByRequestIdForUpdate", Select.class))
                .isEqualTo("SELECT * FROM lottery_draw_order WHERE request_id = #{requestId} FOR UPDATE");
    }

    @Test
    void failedTransitionOnlyChangesProcessingRowsAndSetsCompletionTime() throws Exception {
        String sql = sql("markFailedIfProcessing", Update.class);

        assertThat(sql).contains("UPDATE lottery_draw_order", "status = 'FAILED'", "fail_reason = #{safeReason}",
                "completed_at = CURRENT_TIMESTAMP(3)", "WHERE id = #{orderId}", "AND status = 'PROCESSING'");
    }

    @Test
    void mapperDiscoveryIsConfinedToItsLotteryMapperPackage() {
        MapperScan scan = DrawOrderMapperConfiguration.class.getAnnotation(MapperScan.class);

        assertThat(scan).isNotNull();
        assertThat(scan.basePackageClasses()).containsExactly(DrawOrderMapper.class);
    }

    private String sql(String methodName, Class<? extends java.lang.annotation.Annotation> annotationType)
            throws Exception {
        Method method = Arrays.stream(DrawOrderMapper.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        if (annotationType == Insert.class) {
            return String.join(" ", method.getAnnotation(Insert.class).value()).replaceAll("\\s+", " ").trim();
        }
        if (annotationType == Select.class) {
            return String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ").trim();
        }
        return String.join(" ", method.getAnnotation(Update.class).value()).replaceAll("\\s+", " ").trim();
    }
}
