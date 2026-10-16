package com.dongqh.lotteryservice.drawtransaction;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.dongqh.lotteryservice.domain.po.draworder.DrawOrderRow;
import com.dongqh.lotteryservice.mapper.draworder.DrawOrderMapper;
import com.dongqh.lotteryservice.config.drawtransaction.DrawTransactionMapperConfiguration;
import com.dongqh.lotteryservice.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lotteryservice.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lotteryservice.enums.drawtransaction.BenefitStatus;
import com.dongqh.lotteryservice.mapper.drawtransaction.ActivityPrizeInventoryMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lotteryservice.mapper.drawtransaction.UserBenefitMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DrawTransactionMapperContractTests {

    @Test
    void stockDecrementIsConditionalAndUsesOnlyActivityPrizeId() throws Exception {
        String sql = sql(ActivityPrizeInventoryMapper.class, "decrementIfAvailable");

        assertThat(sql).contains("UPDATE marketing_activity_prize", "SET remaining_stock = remaining_stock - 1",
                "WHERE id = #{activityPrizeId}", "AND remaining_stock > 0");
    }

    @Test
    void orderSuccessIsConditionalOnProcessingAndUsesDrawCompletionTime() throws Exception {
        String sql = sql(DrawOrderMapper.class, "markSuccessIfProcessing");

        assertThat(sql).contains("UPDATE lottery_draw_order", "SET status = 'SUCCESS'", "fail_reason = NULL",
                "completed_at = #{completedAt}", "WHERE id = #{orderId}", "AND status = 'PROCESSING'");
    }

    @Test
    void drawRecordAndPendingBenefitRemainMappedToLegacyTablesAndColumns() throws Exception {
        assertThat(DrawRecordRow.class.getAnnotation(TableName.class).value()).isEqualTo("lottery_draw_record");
        assertThat(UserBenefitRow.class.getAnnotation(TableName.class).value()).isEqualTo("user_benefit");
        assertThat(DrawOrderRow.class.getAnnotation(TableName.class).value()).isEqualTo("lottery_draw_order");
        assertFieldColumn(DrawRecordRow.class, "sequenceNo", "sequence_no");
        assertFieldColumn(DrawRecordRow.class, "prizeImageUrl", "prize_image_url");
        assertFieldColumn(DrawRecordRow.class, "rewardFingerprint", "reward_fingerprint");
        assertThat(field(DrawRecordRow.class, "createdAt").getAnnotation(TableField.class).insertStrategy())
                .isEqualTo(FieldStrategy.NEVER);
        assertFieldColumn(UserBenefitRow.class, "drawRecordId", "draw_record_id");
        assertFieldColumn(UserBenefitRow.class, "rewardPayload", "reward_payload");
        assertFieldColumn(UserBenefitRow.class, "obtainedAt", "obtained_at");
        assertThat(BenefitStatus.PENDING.name()).isEqualTo("PENDING");
    }

    @Test
    void transactionMappersAreScannedOnlyFromTheirLotteryPackage() {
        MapperScan scan = DrawTransactionMapperConfiguration.class.getAnnotation(MapperScan.class);

        assertThat(scan).isNotNull();
        assertThat(scan.basePackageClasses()).containsExactly(DrawRecordMapper.class);
        assertThat(Arrays.stream(DrawRecordMapper.class.getPackageName().split("\\.")).toList())
                .contains("drawtransaction");
        assertThat(UserBenefitMapper.class.getPackageName()).isEqualTo(DrawRecordMapper.class.getPackageName());
        assertThat(ActivityPrizeInventoryMapper.class.getPackageName()).isEqualTo(DrawRecordMapper.class.getPackageName());
    }

    private String sql(Class<?> mapperType, String methodName) throws Exception {
        Method method = Arrays.stream(mapperType.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        Update update = method.getAnnotation(Update.class);
        return String.join(" ", update.value()).replaceAll("\\s+", " ").trim();
    }

    private void assertFieldColumn(Class<?> type, String fieldName, String columnName) throws Exception {
        Field field = field(type, fieldName);
        assertThat(field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class).value())
                .isEqualTo(columnName);
    }

    private Field field(Class<?> type, String fieldName) throws NoSuchFieldException {
        return type.getDeclaredField(fieldName);
    }
}
