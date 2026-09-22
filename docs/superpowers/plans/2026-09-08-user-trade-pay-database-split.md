# User, Trade, and Pay Database Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create isolated `hm_user`, `hm_trade`, and `hm_pay` databases from the current `hmall` schema and data, provision least-privilege service accounts, and point the three services' database YAML at those databases.

**Architecture:** The table boundaries come from `mysql/sqls`, while table definitions and rows are copied from the current `hmall` database so project-specific schema additions survive. Three idempotent migrations run through the existing Compose `schema-init` job; the original `hmall` tables remain untouched.

**Tech Stack:** MySQL 8.4, Docker Compose, Spring Boot YAML, Python `unittest`, PowerShell 7

## Global Constraints

- Only database migration, Compose database provisioning, and database connection YAML are in scope.
- Do not modify Java files, Maven POM files, `hm-api`, or OpenFeign clients.
- Target databases are `hm_user`, `hm_trade`, and `hm_pay`.
- Keep all original tables and data in `hmall`.
- Local services connect to Docker MySQL at `127.0.0.1:13307`; Docker profiles connect at `mysql:3306`.
- Store the requested development credentials directly in YAML.
- Preserve unrelated changes in the dirty working tree.

---

### Task 1: Add a static database-split contract test

**Files:**
- Create: `tests/test_user_trade_pay_database_split.py`

**Interfaces:**
- Consumes: the agreed database names, table ownership, account names, passwords, and Compose `schema-init` convention.
- Produces: a regression test that validates the three migration files, Compose wiring, and service YAML connection values.

- [ ] **Step 1: Create the failing contract test**

```python
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class DatabaseSplitContractTest(unittest.TestCase):
    DATABASES = {
        "user": ("hm_user", "hm_user_app", "hmall-lottery-user-dev", ("user", "address")),
        "trade": ("hm_trade", "hm_trade_app", "hmall-lottery-trade-dev", ("order", "order_detail", "order_logistics")),
        "pay": ("hm_pay", "hm_pay_app", "hmall-lottery-pay-dev", ("pay_order",)),
    }

    def read(self, relative_path: str) -> str:
        return (ROOT / relative_path).read_text(encoding="utf-8")

    def test_migrations_copy_current_hmall_tables_without_deleting_sources(self):
        files = {
            "user": "mysql/migrations/V006__create_user_service_database.sql",
            "trade": "mysql/migrations/V007__create_trade_service_database.sql",
            "pay": "mysql/migrations/V008__create_pay_service_database.sql",
        }
        for domain, relative_path in files.items():
            database, account, _, tables = self.DATABASES[domain]
            sql = self.read(relative_path)
            self.assertIn(f"CREATE DATABASE IF NOT EXISTS `{database}`", sql)
            self.assertIn(f"'{account}'@'%'", sql)
            self.assertNotIn("DROP TABLE", sql.upper())
            self.assertNotIn("DELETE FROM `HMALL`", sql.upper())
            for table in tables:
                self.assertIn(
                    f"CREATE TABLE IF NOT EXISTS `{database}`.`{table}` LIKE `hmall`.`{table}`",
                    sql,
                )
                self.assertIn(
                    f"INSERT IGNORE INTO `{database}`.`{table}` SELECT * FROM `hmall`.`{table}`",
                    sql,
                )

    def test_compose_runs_all_three_migrations_with_yaml_credentials(self):
        compose = self.read("compose.yaml")
        for domain, (database, _, password, _) in self.DATABASES.items():
            migration_number = {"user": "006", "trade": "007", "pay": "008"}[domain]
            variable = f"HM_{domain.upper()}_DB_PASSWORD"
            self.assertIn(f"{variable}: {password}", compose)
            self.assertIn(f"V{migration_number}__create_{domain}_service_database.sql", compose)
            self.assertIn(f"@{database}_password", compose)

    def test_service_profiles_use_isolated_databases_and_literal_passwords(self):
        for domain, (database, account, password, _) in self.DATABASES.items():
            local = self.read(f"{domain}-service/src/main/resources/application-local.yaml")
            dev = self.read(f"{domain}-service/src/main/resources/application-dev.yaml")
            for content in (local, dev):
                self.assertIn(f"database: {database}", content)
                self.assertIn(f"username: {account}", content)
                self.assertIn(f"password: {password}", content)
            self.assertIn("host: 127.0.0.1", local)
            self.assertIn("port: 13307", local)
            self.assertIn("host: mysql", dev)
            self.assertIn("port: 3306", dev)

    def test_trade_and_pay_base_yaml_define_datasource(self):
        for domain in ("trade", "pay"):
            application = self.read(f"{domain}-service/src/main/resources/application.yaml")
            self.assertIn("active: ${SPRING_PROFILES_ACTIVE:local}", application)
            self.assertIn("jdbc:mysql://${hm.db.host}:${hm.db.port}/${hm.db.database}", application)
            self.assertIn("username: ${hm.db.username}", application)
            self.assertIn("password: ${hm.db.password}", application)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the contract test and confirm it fails for missing migrations/configuration**

Run: `python -m unittest tests.test_user_trade_pay_database_split -v`

Expected: FAIL because the V006–V008 files and trade/pay profile YAML files do not yet exist.

- [ ] **Step 3: Commit the test**

```powershell
git add -- tests/test_user_trade_pay_database_split.py
git commit -m "test: define user trade pay database split contract"
```

### Task 2: Create the isolated database migrations

**Files:**
- Create: `mysql/migrations/V006__create_user_service_database.sql`
- Create: `mysql/migrations/V007__create_trade_service_database.sql`
- Create: `mysql/migrations/V008__create_pay_service_database.sql`

**Interfaces:**
- Consumes: session variables `@hm_user_password`, `@hm_trade_password`, and `@hm_pay_password`, supplied by Compose.
- Produces: three schemas, three restricted MySQL users, copied table definitions, and copied data.

- [ ] **Step 1: Create each migration using the existing V004/V005 provisioning pattern**

For each target, implement this exact sequence, substituting its database, account, password variable, and table list:

```sql
CREATE DATABASE IF NOT EXISTS `hm_user`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SET @hm_user_password = COALESCE(@hm_user_password, 'hmall-lottery-user-dev');
SET @create_hm_user_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'hm_user_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_user_password)
);
PREPARE create_hm_user_stmt FROM @create_hm_user_sql;
EXECUTE create_hm_user_stmt;
DEALLOCATE PREPARE create_hm_user_stmt;

SET @alter_hm_user_sql = CONCAT(
    "ALTER USER 'hm_user_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_user_password)
);
PREPARE alter_hm_user_stmt FROM @alter_hm_user_sql;
EXECUTE alter_hm_user_stmt;
DEALLOCATE PREPARE alter_hm_user_stmt;

REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'hm_user_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `hm_user`.* TO 'hm_user_app'@'%';

CREATE TABLE IF NOT EXISTS `hm_user`.`user` LIKE `hmall`.`user`;
CREATE TABLE IF NOT EXISTS `hm_user`.`address` LIKE `hmall`.`address`;
INSERT IGNORE INTO `hm_user`.`user` SELECT * FROM `hmall`.`user`;
INSERT IGNORE INTO `hm_user`.`address` SELECT * FROM `hmall`.`address`;
```

Use the same full account-provisioning block in V007 for `hm_trade_app`/`@hm_trade_password`, followed by `order`, `order_detail`, and `order_logistics`. Use it in V008 for `hm_pay_app`/`@hm_pay_password`, followed by `pay_order`. Quote every database and table identifier, especially `order` and `user`.

- [ ] **Step 2: Run the migration part of the contract test**

Run: `python -m unittest tests.test_user_trade_pay_database_split.DatabaseSplitContractTest.test_migrations_copy_current_hmall_tables_without_deleting_sources -v`

Expected: PASS.

- [ ] **Step 3: Commit the migrations**

```powershell
git add -- mysql/migrations/V006__create_user_service_database.sql mysql/migrations/V007__create_trade_service_database.sql mysql/migrations/V008__create_pay_service_database.sql
git commit -m "feat: add user trade pay database migrations"
```

### Task 3: Wire the migrations into Docker Compose

**Files:**
- Modify: `compose.yaml`

**Interfaces:**
- Consumes: the V006–V008 SQL files and the existing healthy `mysql` dependency.
- Produces: literal YAML password values and a `schema-init` execution for all three migrations.

- [ ] **Step 1: Extend `schema-init.environment` and mount all migrations**

Add these environment entries:

```yaml
      HM_USER_DB_PASSWORD: hmall-lottery-user-dev
      HM_TRADE_DB_PASSWORD: hmall-lottery-trade-dev
      HM_PAY_DB_PASSWORD: hmall-lottery-pay-dev
```

Add these read-only volumes:

```yaml
      - ./mysql/migrations/V006__create_user_service_database.sql:/migrations/V006__create_user_service_database.sql:ro
      - ./mysql/migrations/V007__create_trade_service_database.sql:/migrations/V007__create_trade_service_database.sql:ro
      - ./mysql/migrations/V008__create_pay_service_database.sql:/migrations/V008__create_pay_service_database.sql:ro
```

- [ ] **Step 2: Extend `schema-init.command` after V005 and before Nacos**

For each domain, hex-encode its password and call MySQL with the matching session variable. The user block is:

```sh
        user_password_hex="$$(printf '%s' "$$HM_USER_DB_PASSWORD" | od -An -tx1 | tr -d ' \n')"
        mysql --protocol=TCP -h mysql -uroot -p"$$MYSQL_ROOT_PASSWORD" \
          --init-command="SET @hm_user_password = CONVERT(X'$${user_password_hex}' USING utf8mb4)" \
          < /migrations/V006__create_user_service_database.sql
```

Repeat with `trade_password_hex`/`@hm_trade_password`/V007 and `pay_password_hex`/`@hm_pay_password`/V008.

- [ ] **Step 3: Validate Compose and its contract**

Run: `docker compose config --quiet`

Expected: exit code 0.

Run: `python -m unittest tests.test_user_trade_pay_database_split.DatabaseSplitContractTest.test_compose_runs_all_three_migrations_with_yaml_credentials -v`

Expected: PASS.

- [ ] **Step 4: Commit the Compose change**

```powershell
git add -- compose.yaml
git commit -m "feat: provision user trade pay databases in compose"
```

### Task 4: Configure the three services' database YAML

**Files:**
- Modify: `user-service/src/main/resources/application-local.yaml`
- Modify: `user-service/src/main/resources/application-dev.yaml`
- Create: `trade-service/src/main/resources/application-local.yaml`
- Create: `trade-service/src/main/resources/application-dev.yaml`
- Modify: `trade-service/src/main/resources/application.yaml`
- Create: `pay-service/src/main/resources/application-local.yaml`
- Create: `pay-service/src/main/resources/application-dev.yaml`
- Modify: `pay-service/src/main/resources/application.yaml`

**Interfaces:**
- Consumes: the database names and credentials provisioned by Tasks 2–3.
- Produces: local and Docker database connection properties for each service.

- [ ] **Step 1: Write each local profile with literal connection values**

Use this shape, substituting the domain-specific database, account, and password:

```yaml
hm:
  db:
    host: 127.0.0.1
    port: 13307
    database: hm_user
    username: hm_user_app
    password: hmall-lottery-user-dev
```

- [ ] **Step 2: Write each dev profile with literal connection values**

Use this shape, substituting the domain-specific database, account, and password:

```yaml
hm:
  db:
    host: mysql
    port: 3306
    database: hm_user
    username: hm_user_app
    password: hmall-lottery-user-dev
```

- [ ] **Step 3: Add profile and datasource wiring only where missing**

Keep `user-service/application.yaml` database wiring and unrelated settings unchanged. In trade and pay base YAML, retain the service name and add:

```yaml
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  datasource:
    url: jdbc:mysql://${hm.db.host}:${hm.db.port}/${hm.db.database}?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&serverTimezone=Asia/Shanghai
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${hm.db.username}
    password: ${hm.db.password}
```

- [ ] **Step 4: Run all static database-split tests**

Run: `python -m unittest tests.test_user_trade_pay_database_split -v`

Expected: all tests PASS.

- [ ] **Step 5: Confirm scope and commit YAML changes**

Run: `git diff --name-only -- user-service trade-service pay-service`

Expected: only the eight resource YAML files listed in this task; no Java or POM files.

```powershell
git add -- user-service/src/main/resources/application-local.yaml user-service/src/main/resources/application-dev.yaml trade-service/src/main/resources/application-local.yaml trade-service/src/main/resources/application-dev.yaml trade-service/src/main/resources/application.yaml pay-service/src/main/resources/application-local.yaml pay-service/src/main/resources/application-dev.yaml pay-service/src/main/resources/application.yaml
git commit -m "config: point services at isolated databases"
```

### Task 5: Execute and verify the database split

**Files:**
- No source files changed.

**Interfaces:**
- Consumes: the updated Compose `schema-init` job and running Docker MySQL.
- Produces: verified database schemas, copied data, idempotency evidence, and privilege-isolation evidence.

- [ ] **Step 1: Recreate and run the one-shot schema initializer**

Run: `docker compose up -d mysql`

Expected: MySQL becomes healthy.

Run: `docker compose run --rm schema-init`

Expected: exit code 0 with no SQL error.

- [ ] **Step 2: Verify target databases and exact table ownership**

Load `MYSQL_ROOT_PASSWORD` from `.env` into a task-specific PowerShell variable without printing it, then run MySQL in the container:

```powershell
$dbRootPassword = ((Get-Content -Encoding UTF8 .env | Where-Object { $_ -like 'MYSQL_ROOT_PASSWORD=*' }) -split '=', 2)[1]
docker compose exec -T mysql mysql -uroot "-p$dbRootPassword" -N -e "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema IN ('hm_user','hm_trade','hm_pay') ORDER BY table_schema, table_name;"
```

Expected rows:

```text
hm_pay   pay_order
hm_trade order
hm_trade order_detail
hm_trade order_logistics
hm_user  address
hm_user  user
```

- [ ] **Step 3: Verify columns and row counts match the source**

Run:

```powershell
$columnSql = @'
SELECT pairs.target_schema, pairs.table_name, source_columns.column_count, target_columns.column_count
FROM (
    SELECT 'hm_user' target_schema, 'user' table_name UNION ALL
    SELECT 'hm_user', 'address' UNION ALL
    SELECT 'hm_trade', 'order' UNION ALL
    SELECT 'hm_trade', 'order_detail' UNION ALL
    SELECT 'hm_trade', 'order_logistics' UNION ALL
    SELECT 'hm_pay', 'pay_order'
) pairs
JOIN (
    SELECT table_name, COUNT(*) column_count
    FROM information_schema.columns
    WHERE table_schema = 'hmall'
    GROUP BY table_name
) source_columns ON source_columns.table_name = pairs.table_name
JOIN (
    SELECT table_schema, table_name, COUNT(*) column_count
    FROM information_schema.columns
    WHERE table_schema IN ('hm_user', 'hm_trade', 'hm_pay')
    GROUP BY table_schema, table_name
) target_columns ON target_columns.table_schema = pairs.target_schema
    AND target_columns.table_name = pairs.table_name
WHERE source_columns.column_count <> target_columns.column_count;
'@
docker compose exec -T mysql mysql -uroot "-p$dbRootPassword" -N -e $columnSql
```

Expected: no result rows.

Run:

```powershell
$countSql = @'
SELECT 'user', (SELECT COUNT(*) FROM `hmall`.`user`), (SELECT COUNT(*) FROM `hm_user`.`user`)
UNION ALL SELECT 'address', (SELECT COUNT(*) FROM `hmall`.`address`), (SELECT COUNT(*) FROM `hm_user`.`address`)
UNION ALL SELECT 'order', (SELECT COUNT(*) FROM `hmall`.`order`), (SELECT COUNT(*) FROM `hm_trade`.`order`)
UNION ALL SELECT 'order_detail', (SELECT COUNT(*) FROM `hmall`.`order_detail`), (SELECT COUNT(*) FROM `hm_trade`.`order_detail`)
UNION ALL SELECT 'order_logistics', (SELECT COUNT(*) FROM `hmall`.`order_logistics`), (SELECT COUNT(*) FROM `hm_trade`.`order_logistics`)
UNION ALL SELECT 'pay_order', (SELECT COUNT(*) FROM `hmall`.`pay_order`), (SELECT COUNT(*) FROM `hm_pay`.`pay_order`);
'@
docker compose exec -T mysql mysql -uroot "-p$dbRootPassword" -N -e $countSql
```

Expected: the second and third numeric values are equal on every row.

- [ ] **Step 4: Verify idempotency**

Run: `docker compose run --rm schema-init`

Expected: exit code 0.

Repeat the row-count comparison. Expected: counts remain unchanged and still match `hmall`.

- [ ] **Step 5: Verify own-database access and cross-database denial**

For each service account, connect through `127.0.0.1:13307` using its YAML password and run `SELECT COUNT(*)` against its own table. Expected: success.

Then attempt one `SELECT` against another isolated database. Examples:

```powershell
docker compose exec -T mysql mysql -uhm_user_app -phmall-lottery-user-dev -N -e "SELECT COUNT(*) FROM hm_user.user;"
docker compose exec -T mysql mysql -uhm_user_app -phmall-lottery-user-dev -N -e "SELECT COUNT(*) FROM hm_trade.order;"
```

Expected: first command succeeds; second fails with `SELECT command denied`.

- [ ] **Step 6: Run final regression and scope checks**

Run: `python -m unittest tests.test_user_trade_pay_database_split tests.test_infrastructure_contract -v`

Expected: all tests PASS.

Run: `git diff --check`

Expected: no whitespace errors introduced by this work.

Run: `git status --short`

Expected: pre-existing unrelated changes may remain, but no uncommitted file from this plan remains.
