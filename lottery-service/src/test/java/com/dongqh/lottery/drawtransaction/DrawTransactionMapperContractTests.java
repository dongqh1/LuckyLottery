package com.dongqh.lottery.drawtransaction;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.dongqh.lottery.domain.po.draworder.DrawOrderRow;
import com.dongqh.lottery.mapper.draworder.DrawOrderMapper;
import com.dongqh.lottery.config.drawtransaction.DrawTransactionMapperConfiguration;
import com.dongqh.lottery.domain.po.drawtransaction.DrawRecordRow;
import com.dongqh.lottery.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lottery.enums.drawtransaction.BenefitStatus;
import com.dongqh.lottery.mapper.drawtransaction.ActivityPrizeInventoryMapper;
import com.dongqh.lottery.mapper.drawtransaction.DrawRecordMapper;
import com.dongqh.lottery.mapper.drawtransaction.UserBenefitMapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
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
    void drawRecordAndPendingBenefitMapOnlyRetainedBusinessColumns() throws Exception {
        assertThat(DrawRecordRow.class.getAnnotation(TableName.class).value()).isEqualTo("lottery_draw_record");
        assertThat(UserBenefitRow.class.getAnnotation(TableName.class).value()).isEqualTo("user_benefit");
        assertThat(DrawOrderRow.class.getAnnotation(TableName.class).value()).isEqualTo("lottery_draw_order");
        assertFieldColumn(DrawRecordRow.class, "sequenceNo", "sequence_no");
        assertThat(field(DrawRecordRow.class, "createdAt").getAnnotation(TableField.class).insertStrategy())
                .isEqualTo(FieldStrategy.NEVER);
        assertFieldColumn(UserBenefitRow.class, "drawRecordId", "draw_record_id");
        assertFieldColumn(UserBenefitRow.class, "obtainedAt", "obtained_at");
        assertThat(Arrays.stream(DrawRecordRow.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("rewardPayload", "rewardFingerprint");
        assertThat(Arrays.stream(UserBenefitRow.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("rewardPayload", "rewardFingerprint");
        assertThat(BenefitStatus.PENDING.name()).isEqualTo("PENDING");
    }

    @Test
    void drawHistoryReadsCurrentPrizeAndRewardDetails() throws Exception {
        for (String method : new String[]{"selectByOrderId", "selectMinePage", "selectAdminPage", "selectByOrderIds"}) {
            String sql = selectSql(DrawRecordMapper.class, method);
            assertThat(sql).contains("JOIN marketing_prize p ON p.id = r.prize_id",
                    "LEFT JOIN reward_definition rd ON rd.id = p.reward_definition_id",
                    "p.prize_name", "p.prize_type", "p.image_url", "rd.reward_type", "rd.target_id", "rd.quantity");
        }
    }

    @Test
    void benefitQueriesFilterAndProjectCurrentPrizeAndRewardDetails() throws Exception {
        String pageSql = selectSql(UserBenefitMapper.class, "selectMinePage");
        String countSql = selectSql(UserBenefitMapper.class, "countMine");
        String lockedSql = selectSql(UserBenefitMapper.class, "selectByIdForUpdate");

        for (String sql : new String[]{pageSql, countSql, lockedSql}) {
            assertThat(sql).contains("JOIN marketing_prize p ON p.id = b.prize_id",
                    "LEFT JOIN reward_definition rd ON rd.id = p.reward_definition_id",
                    "p.prize_type");
        }
        assertThat(pageSql).contains("p.prize_name", "p.image_url", "rd.reward_type", "rd.target_id", "rd.quantity")
                .doesNotContain("b.reward_payload", "b.reward_fingerprint");
    }

    @Test
    void drawRecordSnapshotFieldsAreProjectionOnlyOrRemoved() throws Exception {
        for (String name : new String[]{"prizeName", "prizeType", "prizeImageUrl", "rewardDefinitionId",
                "rewardType", "rewardTargetId", "rewardQuantity"}) {
            assertThat(field(DrawRecordRow.class, name).getAnnotation(TableField.class).exist())
                    .as(name + " must be projected from the current catalog")
                    .isFalse();
        }
        assertThat(Arrays.stream(DrawRecordRow.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("rewardPayload", "rewardFingerprint");
        for (String name : new String[]{"prizeType", "rewardDefinitionId", "rewardType", "rewardTargetId",
                "rewardQuantity"}) {
            Field projection = field(UserBenefitRow.class, name);
            assertThat(projection.getAnnotation(TableField.class).exist())
                    .as(name + " must be projected from the current catalog")
                    .isFalse();
        }
        assertThat(Arrays.stream(UserBenefitRow.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("rewardPayload", "rewardFingerprint");
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

    private String selectSql(Class<?> mapperType, String methodName) throws Exception {
        Method method = Arrays.stream(mapperType.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        return String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ").trim();
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
