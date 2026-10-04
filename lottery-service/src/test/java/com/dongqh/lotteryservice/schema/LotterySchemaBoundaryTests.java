package com.dongqh.lotteryservice.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LotterySchemaBoundaryTests {

    @Test
    void migrationOwnsLotteryTablesWithoutIdentityOrCatalogTablesOrDataWrites() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V1__create_lottery_schema.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains(
                    "CREATE TABLE marketing_prize",
                    "CREATE TABLE marketing_activity",
                    "CREATE TABLE marketing_activity_prize",
                    "CREATE TABLE lottery_draw_order",
                    "CREATE TABLE lottery_draw_record",
                    "CREATE TABLE user_benefit",
                    "CREATE TABLE reward_definition",
                    "CREATE TABLE draw_chance_account",
                    "CREATE TABLE draw_chance_ledger",
                    "CREATE TABLE draw_chance_reservation",
                    "CREATE TABLE lottery_reward_quarantine",
                    "CREATE TABLE message_outbox",
                    "CREATE TABLE message_consume_record");
            assertThat(sql).doesNotContain(
                    "CREATE TABLE sys_user",
                    "CREATE TABLE sys_role",
                    "CREATE TABLE sys_permission",
                    "CREATE TABLE product (",
                    "CREATE TABLE product_sku",
                    "INSERT INTO",
                    "ALTER TABLE");
        }
    }
}
