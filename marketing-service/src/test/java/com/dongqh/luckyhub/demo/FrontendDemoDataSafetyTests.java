package com.dongqh.luckyhub.demo;

import com.dongqh.luckyhub.security.auth.PasswordService;
import com.dongqh.luckyhub.domain.crypto.shipping.AddressCipher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.task.scheduling.enabled=false",
        "luckyhub.lottery.reconciliation-enabled=false",
        "luckyhub.messaging.enabled=false",
        "luckyhub.fulfillment.initial-delay=24h",
        "luckyhub.fulfillment.projection-initial-delay=24h"
})
@Import(DemoSchedulingDisabledTestConfiguration.class)
class FrontendDemoDataSafetyTests {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordService passwords;

    @Autowired
    AddressCipher addressCipher;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void scheduledAnnotationProcessorIsAbsentFromDemoContext() {
        assertThat(applicationContext.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class))
                .isEmpty();
    }

    @Test
    void javaGuardRejectsRemoteHostReportedByJdbcMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        var metadata = mock(java.sql.DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:mysql://mysql.production.example.com:3306/luckyhub");
        assertThatThrownBy(() -> FrontendDemoDataSeederTests.assertLocalDatabaseConnection(
                connection, "luckyhub"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("本机");
    }

    @Test
    void javaGuardAcceptsOnlyNormalizedLocalJdbcMetadataHosts() throws Exception {
        for (String url : java.util.List.of(
                "jdbc:mysql://localhost:3306/luckyhub",
                "jdbc:mysql://127.0.0.1:3306/luckyhub",
                "jdbc:mysql://[::1]:3306/luckyhub")) {
            Connection connection = mock(Connection.class);
            var metadata = mock(java.sql.DatabaseMetaData.class);
            var statement = mock(java.sql.PreparedStatement.class);
            var resultSet = mock(java.sql.ResultSet.class);
            var schemaStatement = mock(java.sql.PreparedStatement.class);
            var schemaResultSet = mock(java.sql.ResultSet.class);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getURL()).thenReturn(url);
            when(connection.prepareStatement("SELECT DATABASE()")).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getString(1)).thenReturn("luckyhub");
            when(connection.prepareStatement(FrontendDemoDataSeederTests.SCHEMA_VERSION_SQL))
                    .thenReturn(schemaStatement);
            when(schemaStatement.executeQuery()).thenReturn(schemaResultSet);
            when(schemaResultSet.next()).thenReturn(true);
            when(schemaResultSet.getInt(1)).thenReturn(17);

            FrontendDemoDataSeederTests.assertLocalDatabaseConnection(connection, "luckyhub");
            verify(connection).prepareStatement(FrontendDemoDataSeederTests.SCHEMA_VERSION_SQL);
        }
    }

    @Test
    void javaGuardRejectsSchemaOlderThanRequiredVersionOnSameConnection() throws Exception {
        Connection connection = localConnectionAtSchemaVersion(16);

        assertThatThrownBy(() -> FrontendDemoDataSeederTests.assertLocalDatabaseConnection(
                connection, "luckyhub"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("V17");
    }

    @Test
    void secondRunIsIdempotentAndNonDemoUsersRemainByteForByteUnchanged() {
        UserSnapshot before = snapshotNonDemoUsers();

        FrontendDemoDataSeederTests.seed(jdbc, passwords, addressCipher);
        Map<String, Long> firstCounts = FrontendDemoDataSeederTests.demoCounts(jdbc);
        UserSnapshot afterFirstRun = snapshotNonDemoUsers();

        FrontendDemoDataSeederTests.seed(jdbc, passwords, addressCipher);
        Map<String, Long> secondCounts = FrontendDemoDataSeederTests.demoCounts(jdbc);
        UserSnapshot afterSecondRun = snapshotNonDemoUsers();

        assertThat(firstCounts).isEqualTo(secondCounts);
        assertThat(afterFirstRun).isEqualTo(before);
        assertThat(afterSecondRun).isEqualTo(before);
    }

    @Test
    void wrapperRejectsMissingExplicitConfirmationBeforeStartingMaven() throws Exception {
        ProcessResult result = runWrapper(Map.of("MYSQL_HOST", "localhost"));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("ConfirmLocalDemoData").contains("YES");
    }

    @Test
    void wrapperRejectsNonLocalDatabaseHostEvenWhenConfirmed() throws Exception {
        ProcessResult result = runWrapper(
                Map.of("MYSQL_HOST", "mysql.production.example.com"),
                "-ConfirmLocalDemoData", "YES");

        assertThat(result.exitCode()).isNotZero();
        // Windows 子进程输出的本地化字符可能受控制台代码页影响，稳定断言主机和 MySQL 标记。
        assertThat(result.output()).contains("MySQL").contains("mysql.production.example.com");
    }

    @Test
    void wrapperRejectsSpringDatasourceUrlOverrideBeforeMaven() throws Exception {
        ProcessResult result = runWrapper(
                Map.of("SPRING_DATASOURCE_URL", "blocked-by-safety-test"),
                "-ConfirmLocalDemoData", "YES");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("SPRING_DATASOURCE_URL");
        assertThat(result.output()).doesNotContain("Scanning for projects");
    }

    @Test
    void wrapperRejectsSpringApplicationJsonOverrideBeforeMaven() throws Exception {
        ProcessResult result = runWrapper(
                Map.of("SPRING_APPLICATION_JSON", "{\"spring.datasource.url\":\"blocked-by-safety-test\"}"),
                "-ConfirmLocalDemoData", "YES");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("SPRING_APPLICATION_JSON");
        assertThat(result.output()).doesNotContain("Scanning for projects");
    }

    @Test
    void wrapperRejectsMavenArgsDatasourceOverrideBeforeMaven() throws Exception {
        ProcessResult result = runWrapper(Map.of(
                        "MAVEN_ARGS", "-Dspring.datasource.url=jdbc:mysql://127.0.0.1:1/luckyhub"),
                "-ConfirmLocalDemoData", "YES");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("MAVEN_ARGS");
        assertThat(result.output()).doesNotContain("Scanning for projects");
    }

    @Test
    void wrapperRejectsMavenArgsFlywayOverrideBeforeMaven() throws Exception {
        ProcessResult result = runWrapper(Map.of(
                        "MAVEN_ARGS", "-Dspring.flyway.enabled=true -Dspring.datasource.url=jdbc:mysql://127.0.0.1:1/luckyhub"),
                "-ConfirmLocalDemoData", "YES");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("MAVEN_ARGS");
        assertThat(result.output()).doesNotContain("Scanning for projects");
    }

    @Test
    void wrapperRejectsSpringFlywayUrlOverrideBeforeMaven() throws Exception {
        ProcessResult result = runWrapper(
                Map.of("SPRING_FLYWAY_URL", "jdbc:mysql://127.0.0.1:1/luckyhub"),
                "-ConfirmLocalDemoData", "YES");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("SPRING_FLYWAY_URL");
        assertThat(result.output()).doesNotContain("Scanning for projects");
    }

    @Test
    void wrapperRejectsMavenProjectBasedirOverrideBeforeMaven() throws Exception {
        ProcessResult result = runWrapper(
                Map.of(
                        "MAVEN_PROJECTBASEDIR", "C:\\untrusted-maven-root",
                        "MYSQL_HOST", "mysql.production.example.com"),
                "-ConfirmLocalDemoData", "YES");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("MAVEN_PROJECTBASEDIR");
        assertThat(result.output()).doesNotContain("Scanning for projects");
    }

    @Test
    void wrapperRejectsNonEmptyMavenConfigBeforeMaven() throws Exception {
        assertMavenConfigBlockedBeforeMaven("maven.config",
                "-Dspring.datasource.url=jdbc:mysql://127.0.0.1:1/luckyhub");
    }

    @Test
    void wrapperRejectsNonEmptyJvmConfigBeforeMaven() throws Exception {
        assertMavenConfigBlockedBeforeMaven("jvm.config", "-Dspring.flyway.enabled=true");
    }

    private Connection localConnectionAtSchemaVersion(int version) throws Exception {
        Connection connection = mock(Connection.class);
        var metadata = mock(java.sql.DatabaseMetaData.class);
        var databaseStatement = mock(java.sql.PreparedStatement.class);
        var databaseResultSet = mock(java.sql.ResultSet.class);
        var schemaStatement = mock(java.sql.PreparedStatement.class);
        var schemaResultSet = mock(java.sql.ResultSet.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:mysql://localhost:3306/luckyhub");
        when(connection.prepareStatement("SELECT DATABASE()")).thenReturn(databaseStatement);
        when(databaseStatement.executeQuery()).thenReturn(databaseResultSet);
        when(databaseResultSet.next()).thenReturn(true);
        when(databaseResultSet.getString(1)).thenReturn("luckyhub");
        when(connection.prepareStatement(FrontendDemoDataSeederTests.SCHEMA_VERSION_SQL))
                .thenReturn(schemaStatement);
        when(schemaStatement.executeQuery()).thenReturn(schemaResultSet);
        when(schemaResultSet.next()).thenReturn(true);
        when(schemaResultSet.getInt(1)).thenReturn(version);
        return connection;
    }

    private void assertMavenConfigBlockedBeforeMaven(String fileName, String content) throws Exception {
        Path mavenDirectory = Path.of(".mvn").toAbsolutePath();
        Path configFile = mavenDirectory.resolve(fileName);
        assertThat(configFile).doesNotExist();
        boolean createdDirectory = Files.notExists(mavenDirectory);
        try {
            Files.createDirectories(mavenDirectory);
            Files.writeString(configFile, content, StandardCharsets.UTF_8);
            ProcessResult result = runWrapper(
                    Map.of("MYSQL_HOST", "mysql.production.example.com"),
                    "-ConfirmLocalDemoData", "YES");
            assertThat(result.exitCode()).isNotZero();
            assertThat(result.output()).contains(fileName);
            assertThat(result.output()).doesNotContain("Scanning for projects");
        } finally {
            Files.deleteIfExists(configFile);
            if (createdDirectory) {
                Files.deleteIfExists(mavenDirectory);
            }
        }
    }

    private UserSnapshot snapshotNonDemoUsers() {
        var rows = jdbc.query("""
                SELECT id, username, password, COALESCE(nickname, '') AS nickname, status,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created_at,
                       DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') AS updated_at
                FROM sys_user
                WHERE username NOT LIKE 'demo\\_%' ESCAPE '\\\\'
                ORDER BY id
                """, (resultSet, rowNum) -> new UserSnapshotRow(
                resultSet.getLong("id"), resultSet.getString("username"),
                resultSet.getString("password"), resultSet.getString("nickname"),
                resultSet.getInt("status"), resultSet.getString("created_at"),
                resultSet.getString("updated_at")));
        MessageDigest digest = sha256();
        rows.forEach(row -> {
            updateDigest(digest, Long.toString(row.id()));
            updateDigest(digest, row.username());
            updateDigest(digest, row.password());
            updateDigest(digest, row.nickname());
            updateDigest(digest, Integer.toString(row.status()));
            updateDigest(digest, row.createdAt());
            updateDigest(digest, row.updatedAt());
        });
        String ids = rows.stream().map(row -> Long.toString(row.id()))
                .collect(java.util.stream.Collectors.joining(","));
        return new UserSnapshot(rows.size(), ids, HexFormat.of().formatHex(digest.digest()));
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    private void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private ProcessResult runWrapper(Map<String, String> environment, String... arguments) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows"),
                "PowerShell wrapper contract is Windows-only");
        Path script = Path.of("scripts", "dev", "Initialize-FrontendDemoData.ps1").toAbsolutePath();
        var command = new java.util.ArrayList<String>();
        command.add("pwsh");
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(script.toString());
        command.addAll(java.util.List.of(arguments));

        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().keySet().removeIf(name ->
                name.startsWith("SPRING_DATASOURCE_")
                        || name.startsWith("SPRING_FLYWAY_")
                        || name.equals("SPRING_APPLICATION_JSON")
                        || name.startsWith("SPRING_CONFIG_")
                        || name.equals("MAVEN_ARGS")
                        || name.equals("MAVEN_OPTS")
                        || name.equals("MAVEN_PROJECTBASEDIR")
                        || name.equals("JAVA_TOOL_OPTIONS")
                        || name.equals("_JAVA_OPTIONS")
                        || name.equals("JDK_JAVA_OPTIONS"));
        builder.environment().putAll(environment);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private record UserSnapshot(long count, String ids, String checksum) {
    }

    private record UserSnapshotRow(
            long id, String username, String password, String nickname, int status,
            String createdAt, String updatedAt
    ) {
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
