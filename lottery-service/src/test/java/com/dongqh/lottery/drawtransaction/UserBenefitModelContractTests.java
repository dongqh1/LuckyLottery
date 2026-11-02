package com.dongqh.lottery.drawtransaction;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.lottery.domain.po.drawtransaction.UserBenefitRow;
import com.dongqh.lottery.enums.drawtransaction.BenefitStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class UserBenefitModelContractTests {

    @Test
    void benefitStatusesMatchTheLegacyLifecycleExactly() {
        assertThat(Arrays.stream(BenefitStatus.values()).map(Enum::name)).containsExactly(
                "PENDING", "AVAILABLE", "CLAIM_PENDING", "GRANT_FAILED", "CLAIMED",
                "FULFILLING", "SHIPPED", "DELIVERED", "CLAIM_EXPIRED",
                "FULFILLMENT_FAILED", "FULFILLMENT_TERMINATED");
    }

    @Test
    void benefitFieldsMapEverySchemaColumnWithTheExpectedTypes() throws IOException, NoSuchFieldException {
        List<Column> columns = List.of(
                new Column("id", "id", Long.class),
                new Column("drawRecordId", "draw_record_id", Long.class),
                new Column("userId", "user_id", Long.class),
                new Column("prizeId", "prize_id", Long.class),
                new Column("quantity", "quantity", Integer.class),
                new Column("status", "status", BenefitStatus.class),
                new Column("grantError", "grant_error", String.class),
                new Column("obtainedAt", "obtained_at", LocalDateTime.class),
                new Column("expireAt", "expire_at", LocalDateTime.class),
                new Column("claimDeadline", "claim_deadline", LocalDateTime.class),
                new Column("claimedAt", "claimed_at", LocalDateTime.class),
                new Column("shippingOrderId", "shipping_order_id", Long.class),
                new Column("createdAt", "created_at", LocalDateTime.class),
                new Column("updatedAt", "updated_at", LocalDateTime.class));

        assertThat(UserBenefitRow.class.getAnnotation(TableName.class).value()).isEqualTo("user_benefit");
        assertThat(mappedColumns()).containsExactlyInAnyOrderElementsOf(columns.stream().map(Column::sql).toList());
        assertThat(schemaColumns()).containsExactlyInAnyOrderElementsOf(columns.stream().map(Column::sql).toList());

        for (Column column : columns) {
            Field field = UserBenefitRow.class.getDeclaredField(column.field());
            assertThat(field.getType()).as(column.field() + " Java type").isEqualTo(column.type());
            if (column.field().equals("id")) {
                assertThat(field.getAnnotation(TableId.class).type()).isEqualTo(IdType.AUTO);
            } else {
                assertThat(field.getAnnotation(TableField.class)).as(column.field() + " column mapping")
                        .isNotNull();
                assertThat(field.getAnnotation(TableField.class).value()).isEqualTo(column.sql());
            }
        }
    }

    private Set<String> mappedColumns() {
        Set<String> result = new java.util.HashSet<>();
        for (Field field : UserBenefitRow.class.getDeclaredFields()) {
            TableId id = field.getAnnotation(TableId.class);
            if (id != null) {
                result.add(field.getName());
                continue;
            }
            TableField column = field.getAnnotation(TableField.class);
            if (column != null && column.exist()) {
                result.add(column.value());
            }
        }
        return result;
    }

    private Set<String> schemaColumns() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V1__create_lottery_schema.sql")) {
            assertThat(input).isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            String benefitTable = migration.substring(migration.indexOf("CREATE TABLE user_benefit ("))
                    .split("PRIMARY KEY", 2)[0];
            var matcher = Pattern.compile("(?m)^    ([a-z_]+) (?:BIGINT|INT|VARCHAR|CHAR|JSON|DATETIME)\\b")
                    .matcher(benefitTable);
            Set<String> result = new java.util.HashSet<>();
            while (matcher.find()) {
                result.add(matcher.group(1));
            }
            try (InputStream migrationInput = getClass().getClassLoader().getResourceAsStream(
                    "db/migration/V4__remove_lottery_reward_snapshots.sql")) {
                assertThat(migrationInput).isNotNull();
                String migrationV4 = new String(migrationInput.readAllBytes(), StandardCharsets.UTF_8);
                var dropped = Pattern.compile("(?m)^\\s+DROP COLUMN ([a-z_]+)\\s*,?;?$").matcher(migrationV4);
                while (dropped.find()) {
                    result.remove(dropped.group(1));
                }
            }
            try (InputStream migrationInput = getClass().getClassLoader().getResourceAsStream(
                    "db/migration/V5__remove_legacy_reward_fulfillment_reference.sql")) {
                assertThat(migrationInput).isNotNull();
                String migrationV5 = new String(migrationInput.readAllBytes(), StandardCharsets.UTF_8);
                var dropped = Pattern.compile("(?m)^\\s+DROP COLUMN ([a-z_]+)\\s*,?;?$").matcher(migrationV5);
                while (dropped.find()) {
                    result.remove(dropped.group(1));
                }
            }
            return result;
        }
    }

    private record Column(String field, String sql, Class<?> type) { }
}
