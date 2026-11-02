package com.dongqh.membership.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipSchemaBoundaryTests {

    @Test
    void migrationOwnsMembershipTablesWithoutIdentityTablesOrDataWrites() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V1__create_membership_schema.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains(
                    "CREATE TABLE membership_product",
                    "CREATE TABLE user_membership",
                    "CREATE TABLE membership_grant_record");
            assertThat(sql).doesNotContain(
                    "CREATE TABLE sys_user",
                    "CREATE TABLE sys_role",
                    "CREATE TABLE sys_permission",
                    "INSERT INTO",
                    "ALTER TABLE");
        }
    }
}
