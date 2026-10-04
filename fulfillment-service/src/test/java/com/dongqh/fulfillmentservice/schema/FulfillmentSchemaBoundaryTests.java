package com.dongqh.fulfillmentservice.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentSchemaBoundaryTests {

    @Test
    void migrationOwnsTaskAndOutboxTablesWithoutIdentityWrites() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V1__create_fulfillment_schema.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains(
                    "CREATE TABLE fulfillment_task",
                    "CREATE TABLE fulfillment_attempt",
                    "CREATE TABLE fulfillment_quarantine",
                    "CREATE TABLE fulfillment_outbox");
            assertThat(sql).doesNotContain(
                    "CREATE TABLE sys_user",
                    "CREATE TABLE sys_role",
                    "CREATE TABLE sys_permission",
                    "INSERT INTO",
                    "ALTER TABLE");
        }
    }

    @Test
    void shippingMigrationOwnsOrdersTrackingAndHashedReceiptsWithoutAddressBookOrLotteryWrites()
            throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V2__create_shipping_callback_schema.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains(
                    "CREATE TABLE shipping_order",
                    "CREATE TABLE shipping_tracking_event",
                    "CREATE TABLE shipping_callback_receipt",
                    "nonce_digest CHAR(64)",
                    "signature_digest CHAR(64)");
            assertThat(sql).doesNotContain(
                    "CREATE TABLE user_shipping_address",
                    "CREATE TABLE sys_user",
                    "CREATE TABLE sys_role",
                    "INSERT INTO user_benefit",
                    "UPDATE user_benefit",
                    "ALTER TABLE");
        }
    }

    @Test
    void shippingAddressSnapshotMigrationStoresEncryptedSnapshotWithoutOwningAddressBook()
            throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V3__create_shipping_address_snapshot_schema.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains(
                    "CREATE TABLE shipping_address_snapshot",
                    "receiver_ciphertext TEXT NOT NULL",
                    "phone_ciphertext TEXT NOT NULL",
                    "UNIQUE KEY uk_shipping_snapshot_source");
            assertThat(sql).doesNotContain(
                    "CREATE TABLE user_shipping_address",
                    "CREATE TABLE sys_user",
                    "CREATE TABLE sys_role",
                    "user_benefit",
                    "INSERT INTO",
                    "UPDATE ");
        }
    }
}
