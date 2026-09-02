package com.dongqh.luckyhub.demo;

import com.dongqh.luckyhub.auth.security.PasswordService;
import com.dongqh.luckyhub.shipping.crypto.AddressCipher;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.task.scheduling.enabled=false",
        "luckyhub.lottery.reconciliation-enabled=false",
        "luckyhub.messaging.enabled=false",
        "luckyhub.fulfillment.initial-delay=24h",
        "luckyhub.fulfillment.projection-initial-delay=24h"
})
@Import(DemoSchedulingDisabledTestConfiguration.class)
class FrontendDemoDataSeederTests {

    static final String DEMO_PASSWORD = "LuckyHubDemo!2026";
    static final String SCHEMA_VERSION_SQL = """
            SELECT MAX(CAST(version AS UNSIGNED))
            FROM flyway_schema_history
            WHERE success = 1
            """;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordService passwords;

    @Autowired
    AddressCipher addressCipher;

    @Autowired
    Environment environment;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void scheduledAnnotationProcessorIsAbsentFromDemoContext() {
        assertThat(applicationContext.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class))
                .isEmpty();
    }

    @Test
    void seedsThreeLoginReadyDemoAccountsAndBusinessScenarios() {
        seed(jdbc, passwords, addressCipher);

        assertThat(jdbc.queryForList("""
                SELECT username FROM sys_user
                WHERE username IN ('demo_admin', 'demo_user', 'demo_new')
                ORDER BY username
                """, String.class))
                .containsExactly("demo_admin", "demo_new", "demo_user");

        jdbc.query("""
                SELECT username, password FROM sys_user
                WHERE username IN ('demo_admin', 'demo_user', 'demo_new')
                """, resultSet -> {
            assertThat(resultSet.next()).isTrue();
            do {
                assertThat(resultSet.getString("password"))
                        .doesNotContain(DEMO_PASSWORD)
                        .startsWith("$2");
                assertThat(passwords.matches(DEMO_PASSWORD, resultSet.getString("password"))).isTrue();
            } while (resultSet.next());
            return null;
        });

        assertThat(demoCounts(jdbc))
                .containsEntry("products", 3L)
                .containsEntry("activities", 1L)
                .containsEntry("orders", 2L)
                .containsEntry("benefits", 2L)
                .containsEntry("shipping", 2L);

        String receiverCiphertext = jdbc.queryForObject("""
                SELECT receiver_ciphertext FROM shipping_address_snapshot
                WHERE snapshot_no = 'DEMO-SNAPSHOT-CASH-001'
                """, String.class);
        assertThat(addressCipher.decrypt(receiverCiphertext)).isEqualTo("演示收件人");
    }

    @Test
    void requiresExistingSchemaWithAutomaticFlywayDisabled() {
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class, true)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1
                """, Integer.class)).isEqualTo(17);
    }

    @Test
    void restoresExactRolesForAllThreeDemoAccounts() {
        seed(jdbc, passwords, addressCipher);
        jdbc.update("""
                INSERT IGNORE INTO sys_user_role(user_id, role_id)
                SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.role_code = 'ADMIN'
                WHERE u.username IN ('demo_user', 'demo_new')
                """);

        seed(jdbc, passwords, addressCipher);

        assertThat(roleCodes("demo_admin")).containsExactly("ADMIN", "USER");
        assertThat(roleCodes("demo_user")).containsExactly("USER");
        assertThat(roleCodes("demo_new")).containsExactly("USER");
    }

    @Test
    void doesNotPreloadDemoNewAssetsOrResetExistingStateOnSecondRun() {
        seed(jdbc, passwords, addressCipher);
        Long existingBusinessAssets = jdbc.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM points_ledger l WHERE l.user_id = u.id AND l.business_id NOT LIKE 'DEMO-%') +
                    (SELECT COUNT(*) FROM points_redemption_order r WHERE r.user_id = u.id AND r.redemption_no NOT LIKE 'DEMO-%') +
                    (SELECT COUNT(*) FROM user_coupon c WHERE c.user_id = u.id AND c.coupon_no NOT LIKE 'DEMO-%') +
                    (SELECT COUNT(*) FROM mall_order o WHERE o.user_id = u.id AND o.order_no NOT LIKE 'DEMO-%') +
                    (SELECT COUNT(*) FROM user_benefit b WHERE b.user_id = u.id AND (b.fulfillment_no IS NULL OR b.fulfillment_no NOT LIKE 'DEMO-%')) +
                    (SELECT COUNT(*) FROM shipping_order s WHERE s.target_user_id = u.id AND s.shipping_no NOT LIKE 'DEMO-%')
                FROM sys_user u WHERE u.username = 'demo_new'
                """, Long.class);
        Long existingBalance = jdbc.queryForObject("""
                SELECT balance FROM points_account a
                JOIN sys_user u ON u.id = a.user_id WHERE u.username = 'demo_new'
                """, Long.class);

        seed(jdbc, passwords, addressCipher);

        assertThat(jdbc.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM points_ledger l WHERE l.user_id = u.id AND l.business_id NOT LIKE 'DEMO-%') +
                    (SELECT COUNT(*) FROM points_redemption_order r WHERE r.user_id = u.id AND r.redemption_no NOT LIKE 'DEMO-%') +
                    (SELECT COUNT(*) FROM user_coupon c WHERE c.user_id = u.id AND c.coupon_no NOT LIKE 'DEMO-%') +
                    (SELECT COUNT(*) FROM mall_order o WHERE o.user_id = u.id AND o.order_no NOT LIKE 'DEMO-%') +
                    (SELECT COUNT(*) FROM user_benefit b WHERE b.user_id = u.id AND (b.fulfillment_no IS NULL OR b.fulfillment_no NOT LIKE 'DEMO-%')) +
                    (SELECT COUNT(*) FROM shipping_order s WHERE s.target_user_id = u.id AND s.shipping_no NOT LIKE 'DEMO-%')
                FROM sys_user u WHERE u.username = 'demo_new'
                """, Long.class)).isEqualTo(existingBusinessAssets);
        assertThat(jdbc.queryForObject("""
                SELECT balance FROM points_account a
                JOIN sys_user u ON u.id = a.user_id WHERE u.username = 'demo_new'
                """, Long.class)).isEqualTo(existingBalance);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM points_ledger l JOIN sys_user u ON u.id = l.user_id
                WHERE u.username = 'demo_new' AND l.business_id LIKE 'DEMO-%'
                """, Long.class)).isZero();
    }

    private java.util.List<String> roleCodes(String username) {
        return jdbc.queryForList("""
                SELECT r.role_code FROM sys_user_role ur
                JOIN sys_user u ON u.id = ur.user_id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE u.username = ? ORDER BY r.role_code
                """, String.class, username);
    }

    static void seed(JdbcTemplate jdbc, PasswordService passwords, AddressCipher addressCipher) {
        String expectedDatabase = requireExplicitAuthorization();

        String adminHash = passwords.hash(DEMO_PASSWORD);
        String userHash = passwords.hash(DEMO_PASSWORD);
        String newUserHash = passwords.hash(DEMO_PASSWORD);

        jdbc.execute((Connection connection) -> {
            assertLocalDatabaseConnection(connection, expectedDatabase);
            setSessionVariables(connection, adminHash, userHash, newUserHash, addressCipher);
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("frontend-demo-data.sql"), StandardCharsets.UTF_8));
            return null;
        });
    }

    static java.util.Map<String, Long> demoCounts(JdbcTemplate jdbc) {
        var counts = new java.util.LinkedHashMap<String, Long>();
        counts.put("users", count(jdbc,
                "SELECT COUNT(*) FROM sys_user WHERE username LIKE 'demo\\_%' ESCAPE '\\\\'"));
        counts.put("products", count(jdbc,
                "SELECT COUNT(*) FROM product WHERE product_code LIKE 'DEMO-%'"));
        counts.put("activities", count(jdbc,
                "SELECT COUNT(*) FROM marketing_activity WHERE activity_name LIKE 'DEMO-%'"));
        counts.put("orders", count(jdbc,
                "SELECT COUNT(*) FROM mall_order WHERE order_no LIKE 'DEMO-%'"));
        counts.put("benefits", count(jdbc,
                "SELECT COUNT(*) FROM user_benefit WHERE fulfillment_no LIKE 'DEMO-%'"));
        counts.put("shipping", count(jdbc,
                "SELECT COUNT(*) FROM shipping_order WHERE shipping_no LIKE 'DEMO-%'"));
        return counts;
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        Long result = jdbc.queryForObject(sql, Long.class);
        return result == null ? 0L : result;
    }

    private static String requireExplicitAuthorization() {
        Assumptions.assumeTrue(Boolean.getBoolean("frontend.demo.seed"),
                "只有 -Dfrontend.demo.seed=true 才允许写入本地演示数据");
        String expectedDatabase = System.getProperty("frontend.demo.expected-database", "").trim();
        assertThat(expectedDatabase)
                .as("必须通过 -Dfrontend.demo.expected-database 指定本地数据库")
                .isNotBlank();
        return expectedDatabase;
    }

    static void assertLocalDatabaseConnection(Connection connection, String expectedDatabase) throws SQLException {
        String host = databaseHost(connection);
        if (!Set.of("localhost", "127.0.0.1", "::1").contains(host.toLowerCase())) {
            throw new AssertionError("实际 JDBC 连接不是本机数据库，拒绝初始化演示数据");
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT DATABASE()");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next() || !expectedDatabase.equals(resultSet.getString(1))) {
                throw new AssertionError("实际连接的数据库与明确确认的数据库不一致，拒绝初始化演示数据");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(SCHEMA_VERSION_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next() || resultSet.getInt(1) != 17 || resultSet.wasNull()) {
                throw new AssertionError("本地数据库必须预先迁移到 V17，拒绝初始化演示数据");
            }
        }
    }

    private static String databaseHost(Connection connection) throws SQLException {
        String jdbcUrl = connection.getMetaData().getURL();
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new AssertionError("实际 JDBC 连接不是受支持的本机 MySQL，拒绝初始化演示数据");
        }
        try {
            String host = URI.create(jdbcUrl.substring("jdbc:".length())).getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("missing host");
            }
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            return host;
        } catch (IllegalArgumentException exception) {
            throw new AssertionError("无法安全解析实际 JDBC 连接主机，拒绝初始化演示数据");
        }
    }

    private static void setSessionVariables(
            Connection connection,
            String adminHash,
            String userHash,
            String newUserHash,
            AddressCipher addressCipher
    ) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SET @demo_admin_password_hash = ?,
                    @demo_user_password_hash = ?,
                    @demo_new_password_hash = ?,
                    @demo_receiver_ciphertext = ?,
                    @demo_phone_ciphertext = ?,
                    @demo_province_ciphertext = ?,
                    @demo_city_ciphertext = ?,
                    @demo_district_ciphertext = ?,
                    @demo_detail_ciphertext = ?
                """)) {
            statement.setString(1, adminHash);
            statement.setString(2, userHash);
            statement.setString(3, newUserHash);
            statement.setString(4, addressCipher.encrypt("演示收件人"));
            statement.setString(5, addressCipher.encrypt("13800002026"));
            statement.setString(6, addressCipher.encrypt("演示省"));
            statement.setString(7, addressCipher.encrypt("演示市"));
            statement.setString(8, addressCipher.encrypt("演示区"));
            statement.setString(9, addressCipher.encrypt("本地演示地址，不对应真实住址"));
            statement.execute();
        }
    }
}
