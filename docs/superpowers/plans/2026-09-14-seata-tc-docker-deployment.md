# Seata TC Docker Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the Seata TC database in the existing Docker MySQL instance and deploy a single Seata TC registered with the existing Nacos service.

**Architecture:** Extend the existing repeatable `schema-init` job to provision a least-privilege `seata_app` account and the four TC tables. Run `apache/seata-server:2.4.0.jdk17` with the repository's `seata/` resources, DB storage, Nacos registry, and the existing implicit `hmall-lottery_default` Compose network.

**Tech Stack:** Docker Compose, MySQL 8.4, Apache Seata 2.4.0 JDK 17, Nacos 3.1.1, Python `unittest`/pytest contract tests, PowerShell 7.

## Global Constraints

- Keep every project container on the existing Compose default network named `hmall-lottery_default`.
- Do not connect to, modify, or remove the old `hm-net` network or its stopped containers.
- Pin the TC image to `apache/seata-server:2.4.0.jdk17`.
- Persist TC state in the existing MySQL 8.4 container and `mysql-data` volume.
- Use the dedicated MySQL account `seata_app`; the TC must not use MySQL root.
- Keep secrets in the Git-ignored `.env`; commit only variable names and safe placeholders to `.env.example`.
- Do not add Seata client dependencies, `undo_log` business tables, transaction-group configuration, or `@GlobalTransactional` in this plan.
- Preserve all unrelated dirty and staged workspace changes.

---

### Task 1: Provision the Seata TC Database Repeatably

**Files:**
- Create: `tests/test_seata_infrastructure_contract.py`
- Modify: `mysql/seata/seata-tc.sql`
- Modify: `compose.yaml:45-105`
- Modify: `.env.example`
- Modify locally, do not commit: `.env`

**Interfaces:**
- Consumes: existing `schema-init` job, `MYSQL_ROOT_PASSWORD`, and the mounted MySQL service name `mysql`.
- Produces: database `seata`, user `seata_app` accessible from `%`, tables `global_table`, `branch_table`, `lock_table`, `distributed_lock`, and Compose variable `SEATA_DB_PASSWORD`.

- [ ] **Step 1: Write the failing SQL and schema-init contract tests**

Create `tests/test_seata_infrastructure_contract.py` with:

```python
import json
from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
SEATA_SQL = ROOT / "mysql" / "seata" / "seata-tc.sql"


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def compose_config() -> dict:
    result = subprocess.run(
        [
            "docker", "compose", "--env-file", str(ROOT / ".env.example"),
            "-f", str(ROOT / "compose.yaml"), "config", "--format", "json",
        ],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        raise AssertionError(
            f"docker compose config failed:\n{result.stdout}\n{result.stderr}"
        )
    return json.loads(result.stdout)


class SeataDatabaseContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = SEATA_SQL.read_text(encoding="utf-8")
        cls.normalized = re.sub(r"\s+", " ", cls.sql).lower()

    def test_database_and_dedicated_user_are_provisioned_repeatably(self):
        self.assertRegex(
            self.normalized,
            r"create database if not exists\s+`?seata`?.*character set\s+utf8mb4",
        )
        self.assertIn("create user if not exists 'seata_app'@'%'", self.normalized)
        self.assertIn("alter user ''seata_app''@''%'' identified by", self.normalized)
        self.assertRegex(
            self.normalized,
            r"grant\s+select\s*,\s*insert\s*,\s*update\s*,\s*delete\s+on\s+`?seata`?\.\*\s+to\s+'seata_app'@'%'",
        )
        self.assertNotRegex(
            self.normalized,
            r"grant\s+all privileges.*'seata_app'",
        )

    def test_all_tc_tables_and_distributed_locks_are_idempotent(self):
        for table in (
            "global_table",
            "branch_table",
            "lock_table",
            "distributed_lock",
        ):
            with self.subTest(table=table):
                self.assertRegex(
                    self.normalized,
                    rf"create table if not exists\s+`?{table}`?",
                )
        self.assertEqual(4, len(re.findall(r"insert ignore into\s+`?distributed_lock`?", self.normalized)))

    def test_schema_init_mounts_and_executes_seata_sql_with_injected_password(self):
        service = compose_config()["services"]["schema-init"]
        mounts = {
            mount["target"]: mount for mount in service["volumes"]
        }
        self.assertIn("/migrations/seata-tc.sql", mounts)
        self.assertTrue(mounts["/migrations/seata-tc.sql"]["read_only"])
        self.assertIn("SEATA_DB_PASSWORD", service["environment"])
        command = " ".join(
            service["command"]
            if isinstance(service["command"], list)
            else [service["command"]]
        )
        self.assertIn("SET @seata_db_password", command)
        self.assertIn("/migrations/seata-tc.sql", command)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
python -m pytest tests/test_seata_infrastructure_contract.py -q
```

Expected: FAIL because `seata_app`, least-privilege grants, `INSERT IGNORE`, and the Compose mount/command are missing.

- [ ] **Step 3: Make `seata-tc.sql` repeatable and least-privilege**

At the start of `mysql/seata/seata-tc.sql`, use this account bootstrap before the existing table definitions:

```sql
CREATE DATABASE IF NOT EXISTS `seata`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE `seata`;

SET @seata_db_password = COALESCE(@seata_db_password, 'change-seata-db-password');
CREATE USER IF NOT EXISTS 'seata_app'@'%' IDENTIFIED BY 'temporary-password-replaced-below';
SET @alter_seata_user = CONCAT(
  'ALTER USER ''seata_app''@''%'' IDENTIFIED BY ',
  QUOTE(@seata_db_password)
);
PREPARE alter_seata_user_stmt FROM @alter_seata_user;
EXECUTE alter_seata_user_stmt;
DEALLOCATE PREPARE alter_seata_user_stmt;
GRANT SELECT, INSERT, UPDATE, DELETE ON `seata`.* TO 'seata_app'@'%';
```

Replace the four final seed statements with these idempotent statements:

```sql
INSERT IGNORE INTO `distributed_lock` (`lock_key`, `lock_value`, `expire`)
VALUES ('AsyncCommitting', ' ', 0);
INSERT IGNORE INTO `distributed_lock` (`lock_key`, `lock_value`, `expire`)
VALUES ('RetryCommitting', ' ', 0);
INSERT IGNORE INTO `distributed_lock` (`lock_key`, `lock_value`, `expire`)
VALUES ('RetryRollbacking', ' ', 0);
INSERT IGNORE INTO `distributed_lock` (`lock_key`, `lock_value`, `expire`)
VALUES ('TxTimeoutCheck', ' ', 0);
```

- [ ] **Step 4: Wire the SQL into `schema-init` and local environment**

Add this environment key and read-only mount to `schema-init`:

```yaml
      SEATA_DB_PASSWORD: ${SEATA_DB_PASSWORD:?set SEATA_DB_PASSWORD in .env}
```

```yaml
      - ./mysql/seata/seata-tc.sql:/migrations/seata-tc.sql:ro
```

Change the Nacos SQL command from `exec mysql` to `mysql`, then append:

```yaml
        seata_password_hex="$$(printf '%s' "$$SEATA_DB_PASSWORD" | od -An -tx1 | tr -d ' \n')"
        exec mysql --protocol=TCP -h mysql -uroot -p"$$MYSQL_ROOT_PASSWORD" \
          --init-command="SET @seata_db_password = CONVERT(X'$${seata_password_hex}' USING utf8mb4)" \
          < /migrations/seata-tc.sql
```

Add to `.env.example`:

```dotenv
SEATA_DB_PASSWORD=change-seata-db-password
```

Add the local development value to the Git-ignored `.env`:

```dotenv
SEATA_DB_PASSWORD=hmall-lottery-seata-db-dev
```

- [ ] **Step 5: Run the focused test and verify GREEN**

Run:

```powershell
python -m pytest tests/test_seata_infrastructure_contract.py -q
docker compose --env-file .env config --quiet
```

Expected: all Seata database tests PASS and Compose exits with code 0.

- [ ] **Step 6: Commit only Task 1 files**

```powershell
git add -- tests/test_seata_infrastructure_contract.py mysql/seata/seata-tc.sql compose.yaml .env.example
git commit --only -m "feat: provision Seata TC database" -- tests/test_seata_infrastructure_contract.py mysql/seata/seata-tc.sql compose.yaml .env.example
```

Expected: `.env` and unrelated staged files are not included.

---

### Task 2: Configure and Define the Seata TC Container

**Files:**
- Modify: `tests/test_seata_infrastructure_contract.py`
- Modify: `seata/application.yml`
- Modify: `compose.yaml:106-133`
- Modify: `.env.example`
- Modify locally, do not commit: `.env`

**Interfaces:**
- Consumes: `seata_app`, `SEATA_DB_PASSWORD`, service DNS names `mysql` and `nacos`, and the implicit Compose `default` network.
- Produces: healthy Compose service `seata-server`, console endpoint on host port `SEATA_CONSOLE_PORT`, and TC RPC endpoint on host port `SEATA_RPC_PORT`.

- [ ] **Step 1: Add failing runtime configuration and Compose tests**

Append these classes before the `if __name__ == "__main__"` block in `tests/test_seata_infrastructure_contract.py`:

```python
class SeataRuntimeConfigurationContractTest(unittest.TestCase):
    def test_application_uses_container_dns_and_environment_secrets(self):
        application = read("seata/application.yml")
        self.assertIn("server-addr: nacos:8848", application)
        self.assertIn("url: jdbc:mysql://mysql:3306/seata", application)
        self.assertIn("user: seata_app", application)
        for variable in (
            "NACOS_USERNAME",
            "NACOS_PASSWORD",
            "SEATA_DB_PASSWORD",
            "SEATA_CONSOLE_USERNAME",
            "SEATA_CONSOLE_PASSWORD",
            "SEATA_SECURITY_SECRET_KEY",
        ):
            with self.subTest(variable=variable):
                self.assertIn(f"${{{variable}}}", application)
        self.assertNotIn("password: 123", application)


class SeataComposeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = compose_config()
        cls.services = cls.config["services"]

    def test_tc_uses_pinned_image_resources_and_dependencies(self):
        service = self.services["seata-server"]
        self.assertEqual("apache/seata-server:2.4.0.jdk17", service["image"])
        self.assertEqual(
            "service_completed_successfully",
            service["depends_on"]["schema-init"]["condition"],
        )
        self.assertEqual(
            "service_healthy",
            service["depends_on"]["nacos"]["condition"],
        )
        mounts = {mount["target"]: mount for mount in service["volumes"]}
        self.assertIn("/seata-server/resources", mounts)
        self.assertTrue(mounts["/seata-server/resources"]["read_only"])
        self.assertIn("healthcheck", service)

    def test_tc_and_dependencies_share_only_the_existing_default_network(self):
        for name in ("mysql", "nacos", "seata-server"):
            with self.subTest(service=name):
                self.assertEqual({"default"}, set(self.services[name]["networks"]))
        self.assertEqual(
            "hmall-lottery_default",
            self.config["networks"]["default"]["name"],
        )
        self.assertNotIn("hm-net", json.dumps(self.config))

    def test_tc_exposes_console_rpc_and_receives_only_named_secrets(self):
        service = self.services["seata-server"]
        published = {int(port["target"]): int(port["published"]) for port in service["ports"]}
        self.assertEqual(17099, published[7099])
        self.assertEqual(18091, published[8091])
        for variable in (
            "SEATA_DB_PASSWORD",
            "NACOS_USERNAME",
            "NACOS_PASSWORD",
            "SEATA_CONSOLE_USERNAME",
            "SEATA_CONSOLE_PASSWORD",
            "SEATA_SECURITY_SECRET_KEY",
        ):
            self.assertIn(variable, service["environment"])
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
python -m pytest tests/test_seata_infrastructure_contract.py -q
```

Expected: FAIL because the runtime configuration still contains hard-coded credentials and `seata-server` is not defined.

- [ ] **Step 3: Replace hard-coded Seata runtime credentials**

Update the active values in `seata/application.yml` to:

```yaml
console:
  user:
    username: ${SEATA_CONSOLE_USERNAME}
    password: ${SEATA_CONSOLE_PASSWORD}

seata:
  registry:
    type: nacos
    nacos:
      application: seata-server
      server-addr: nacos:8848
      group: DEFAULT_GROUP
      namespace: ""
      cluster: default
      username: ${NACOS_USERNAME}
      password: ${NACOS_PASSWORD}
  security:
    secretKey: ${SEATA_SECURITY_SECRET_KEY}
  store:
    mode: db
    session:
      mode: db
    lock:
      mode: db
    db:
      datasource: druid
      db-type: mysql
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://mysql:3306/seata?rewriteBatchedStatements=true&serverTimezone=Asia%2FShanghai&useSSL=false
      user: seata_app
      password: ${SEATA_DB_PASSWORD}
```

Preserve all existing retry, recovery, metrics, transport, table-name, pool-size, query-limit, and logging settings.

- [ ] **Step 4: Add the `seata-server` Compose service**

Insert after `nacos`:

```yaml
  seata-server:
    image: apache/seata-server:2.4.0.jdk17
    restart: unless-stopped
    environment:
      SEATA_IP: seata-server
      SEATA_PORT: "8091"
      STORE_MODE: db
      SEATA_DB_PASSWORD: ${SEATA_DB_PASSWORD:?set SEATA_DB_PASSWORD in .env}
      NACOS_USERNAME: ${NACOS_USERNAME:-nacos}
      NACOS_PASSWORD: ${NACOS_PASSWORD:-nacos}
      SEATA_CONSOLE_USERNAME: ${SEATA_CONSOLE_USERNAME:?set SEATA_CONSOLE_USERNAME in .env}
      SEATA_CONSOLE_PASSWORD: ${SEATA_CONSOLE_PASSWORD:?set SEATA_CONSOLE_PASSWORD in .env}
      SEATA_SECURITY_SECRET_KEY: ${SEATA_SECURITY_SECRET_KEY:?set SEATA_SECURITY_SECRET_KEY in .env}
      TZ: Asia/Shanghai
    ports:
      - "${SEATA_CONSOLE_PORT:-17099}:7099"
      - "${SEATA_RPC_PORT:-18091}:8091"
    volumes:
      - ./seata:/seata-server/resources:ro
    depends_on:
      schema-init:
        condition: service_completed_successfully
      nacos:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "bash -ec 'exec 3<>/dev/tcp/127.0.0.1/7099'"]
      interval: 10s
      timeout: 5s
      retries: 30
      start_period: 30s
```

Add to `.env.example`:

```dotenv
SEATA_CONSOLE_USERNAME=seata
SEATA_CONSOLE_PASSWORD=change-seata-console-password
SEATA_SECURITY_SECRET_KEY=change-this-seata-security-key-at-least-32-bytes
SEATA_CONSOLE_PORT=17099
SEATA_RPC_PORT=18091
```

Add local development values to `.env`:

```dotenv
SEATA_CONSOLE_USERNAME=seata
SEATA_CONSOLE_PASSWORD=hmall-lottery-seata-console-dev
SEATA_SECURITY_SECRET_KEY=hmall-lottery-seata-security-key-change-local-dev-2026
SEATA_CONSOLE_PORT=17099
SEATA_RPC_PORT=18091
```

- [ ] **Step 5: Update the existing default-stack assertion**

In `tests/test_infrastructure_contract.py`, add `"seata-server"` to the expected default service set in `ComposeContractTest.test_compose_defines_the_split_services_as_the_default_stack`.

- [ ] **Step 6: Run focused and regression tests and verify GREEN**

Run:

```powershell
python -m pytest tests/test_seata_infrastructure_contract.py tests/test_infrastructure_contract.py -q
docker compose --env-file .env config --quiet
```

Expected: all tests PASS and Compose exits with code 0.

- [ ] **Step 7: Commit only Task 2 files**

```powershell
git add -- tests/test_seata_infrastructure_contract.py tests/test_infrastructure_contract.py seata/application.yml compose.yaml .env.example
git commit --only -m "feat: deploy Seata TC with Docker Compose" -- tests/test_seata_infrastructure_contract.py tests/test_infrastructure_contract.py seata/application.yml compose.yaml .env.example
```

Expected: `.env` and unrelated staged files are not included.

---

### Task 3: Document Seata Operations

**Files:**
- Modify: `tests/test_seata_infrastructure_contract.py`
- Modify: `README.md`

**Interfaces:**
- Consumes: Compose service `seata-server`, host ports `17099` and `18091`, and the repeatable `schema-init` job.
- Produces: operator commands for starting, inspecting, and understanding the TC deployment boundary.

- [ ] **Step 1: Add a failing documentation contract**

Append before the test file's main block:

```python
class SeataDocumentationContractTest(unittest.TestCase):
    def test_readme_documents_tc_start_and_scope(self):
        readme = read("README.md")
        self.assertIn("docker compose --env-file .env up -d seata-server", readme)
        self.assertIn("http://localhost:17099", readme)
        self.assertIn("global_table", readme)
        self.assertIn("undo_log", readme)
        self.assertIn("hmall-lottery_default", readme)
```

- [ ] **Step 2: Run the documentation test and verify RED**

Run:

```powershell
python -m pytest tests/test_seata_infrastructure_contract.py -q
```

Expected: FAIL because README does not document Seata.

- [ ] **Step 3: Add the operational section to README**

Add a `## Seata TC` section documenting:

````markdown
## Seata TC

The default Docker stack provisions the `seata` database and starts
`apache/seata-server:2.4.0.jdk17`. MySQL, Nacos, Seata, and the application
services communicate through `hmall-lottery_default`.

Start or refresh only the TC infrastructure:

```powershell
docker compose --env-file .env up -d seata-server
docker compose ps mysql nacos seata-server
docker compose logs --tail 100 seata-server
```

The console is available at `http://localhost:17099` with the credentials from
`.env`. TC state is stored in `global_table`, `branch_table`, `lock_table`, and
`distributed_lock` in the `seata` database.

This deployment provides the TC only. Before using Seata AT transactions, each
participating business database still needs its own `undo_log` table and each
microservice needs matching Seata client and transaction-group configuration.
````

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```powershell
python -m pytest tests/test_seata_infrastructure_contract.py -q
```

Expected: all tests PASS.

- [ ] **Step 5: Commit only documentation files**

```powershell
git add -- tests/test_seata_infrastructure_contract.py README.md
git commit --only -m "docs: describe Seata TC operations" -- tests/test_seata_infrastructure_contract.py README.md
```

---

### Task 4: Deploy and Verify the TC Against Live MySQL and Nacos

**Files:**
- Verify only: `compose.yaml`
- Verify only: `.env`
- Verify only: `mysql/seata/seata-tc.sql`
- Verify only: `seata/application.yml`

**Interfaces:**
- Consumes: the live `hmall-lottery-mysql-1` and `hmall-lottery-nacos-1` containers and the Compose changes from Tasks 1–3.
- Produces: running healthy `hmall-lottery-seata-server-1`, initialized TC schema, and Nacos registration.

- [ ] **Step 1: Validate all static contracts**

Run:

```powershell
python -m pytest tests/test_seata_infrastructure_contract.py tests/test_infrastructure_contract.py -q
docker compose --env-file .env config --quiet
```

Expected: tests PASS and Compose validation exits with code 0.

- [ ] **Step 2: Execute the repeatable schema job**

Run:

```powershell
docker compose --env-file .env up --force-recreate schema-init
```

Expected: `schema-init` exits with code 0 without restarting or deleting MySQL data.

- [ ] **Step 3: Verify database, account, tables, and lock seeds**

Load the ignored `.env` values into PowerShell variables without printing them, then query with `seata_app`:

```powershell
$seataDbPassword = ((Get-Content -Encoding UTF8 .env | Where-Object { $_ -match '^SEATA_DB_PASSWORD=' }) -split '=', 2)[1]
docker compose exec -T mysql mysql -h 127.0.0.1 -useata_app "-p$seataDbPassword" -N -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='seata' ORDER BY TABLE_NAME; SELECT COUNT(*) FROM seata.distributed_lock;"
```

Expected table output contains `branch_table`, `distributed_lock`, `global_table`, `lock_table`, followed by lock count `4`.

- [ ] **Step 4: Pull and start only the TC service**

Run:

```powershell
docker compose --env-file .env pull seata-server
docker compose --env-file .env up -d seata-server
```

Expected: the pinned Seata image is pulled and `hmall-lottery-seata-server-1` starts without recreating healthy MySQL or Nacos containers.

- [ ] **Step 5: Verify container health, network, DB connection, and Nacos registration evidence**

Run:

```powershell
docker compose ps mysql nacos seata-server
docker inspect hmall-lottery-seata-server-1 --format '{{range $name,$config := .NetworkSettings.Networks}}{{$name}}{{end}}'
docker compose logs --tail 200 seata-server
```

Expected:

- MySQL, Nacos, and Seata report running/healthy.
- Seata reports only `hmall-lottery_default` as its network.
- Seata logs show successful MySQL initialization and successful registration with Nacos, with no authentication or connection errors.

- [ ] **Step 6: Run the full infrastructure regression suite**

Run:

```powershell
python -m pytest tests/test_seata_infrastructure_contract.py tests/test_infrastructure_contract.py tests/test_nacos_shared_config_contract.py -q
```

Expected: all tests PASS with no warnings introduced by this change.

- [ ] **Step 7: Record final repository and runtime status**

Run:

```powershell
git status --short
docker compose ps mysql nacos seata-server
```

Expected: Seata-specific tracked files are committed, unrelated pre-existing changes remain untouched, and all three infrastructure services are healthy.
