# RabbitMQ Java Client Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure the Java AMQP services and Docker Compose to use environment-backed RabbitMQ credentials and the `/dongqh` virtual host without moving configuration into Nacos.

**Architecture:** The root `.env` is the only local secret source, while committed YAML consumes named environment variables. Docker Compose injects container-network connection values into Marketing, Trade, and Pay; local JVM runs use `localhost` defaults. An idempotent broker command creates and authorizes `/dongqh` for an already initialized persistent RabbitMQ volume.

**Tech Stack:** Spring Boot YAML, Docker Compose, RabbitMQ 4.3.5, Python unittest/pytest, PowerShell 7.

## Global Constraints

- Use the RabbitMQ virtual host `/dongqh`.
- Keep real credentials only in the ignored root `.env`.
- Do not add RabbitMQ configuration to Nacos.
- Do not migrate Marketing away from `redis-stream`.
- Do not add exchanges, queues, bindings, publishers, or consumers.

---

### Task 1: Lock the configuration contract

**Files:**
- Modify: `tests/test_rabbitmq_infrastructure_contract.py`

**Interfaces:**
- Consumes: `compose.yaml`, `.env.example`, and the three service `application.yaml` files.
- Produces: Assertions for `/dongqh`, identical Compose environment injection, and standard Spring RabbitMQ properties.

- [ ] **Step 1: Update the contract before production configuration**

Change all expected `/hmall` and root-vhost defaults to `/dongqh`. Replace the Marketing-only Compose assertion with a loop over `marketing-service`, `trade-service`, and `pay-service`. Replace the Marketing-only YAML assertion with a loop over the same three application files, while keeping the `provider: redis-stream` assertion specific to Marketing.

- [ ] **Step 2: Run the focused contract and verify failure**

Run: `python -m pytest tests/test_rabbitmq_infrastructure_contract.py -q`

Expected: failures showing `/hmall`, missing Trade/Pay environment values, hard-coded Trade host, missing Pay settings, or old virtual-host defaults.

---

### Task 2: Configure Java clients and Compose

**Files:**
- Modify: `.env`
- Modify: `.env.example`
- Modify: `compose.yaml`
- Modify: `marketing-service/src/main/resources/application.yaml`
- Modify: `trade-service/src/main/resources/application.yaml`
- Modify: `pay-service/src/main/resources/application.yaml`

**Interfaces:**
- Consumes: `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, and `RABBITMQ_VHOST`.
- Produces: Spring Boot `spring.rabbitmq` connection properties for Marketing, Trade, and Pay.

- [ ] **Step 1: Set project environment defaults**

Set `RABBITMQ_VHOST=/dongqh` in `.env` and `.env.example`. Preserve the existing real username and password in `.env`; keep replaceable examples in `.env.example`.

- [ ] **Step 2: Use one Spring RabbitMQ YAML shape in all three services**

Under `spring`, ensure every target service contains:

```yaml
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:}
    password: ${RABBITMQ_PASSWORD:}
    virtual-host: ${RABBITMQ_VHOST:/dongqh}
```

Do not add Nacos imports for RabbitMQ. Preserve all existing non-RabbitMQ configuration.

- [ ] **Step 3: Inject container-network values through Compose**

Set the broker default to `${RABBITMQ_VHOST:-/dongqh}`. Add this exact environment block to `trade-service`, `pay-service`, and retain it for `marketing-service`:

```yaml
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: "5672"
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME:?set RABBITMQ_USERNAME in .env}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:?set RABBITMQ_PASSWORD in .env}
      RABBITMQ_VHOST: ${RABBITMQ_VHOST:-/dongqh}
```

Do not add RabbitMQ as a startup dependency because the services do not yet require it for current business behavior.

- [ ] **Step 4: Run configuration contracts**

Run: `python -m pytest tests/test_rabbitmq_infrastructure_contract.py -q`

Expected: all RabbitMQ configuration contract tests pass.

Run: `docker compose --env-file .env config --quiet`

Expected: exit code 0.

---

### Task 3: Provision and verify the existing broker

**Files:**
- No source file changes.

**Interfaces:**
- Consumes: running Compose RabbitMQ container and credentials from `.env`.
- Produces: virtual host `/dongqh` with full permissions for the configured user.

- [ ] **Step 1: Start RabbitMQ if necessary**

Run: `docker compose --env-file .env up -d rabbitmq`

Expected: the RabbitMQ service is running or started successfully.

- [ ] **Step 2: Create the virtual host idempotently and grant permissions**

Resolve the container ID and username without printing the password, then create and authorize the virtual host:

```powershell
$rabbitContainer = docker compose --env-file .env ps -q rabbitmq
$rabbitUser = (Get-Content -Encoding UTF8 .env | Where-Object { $_ -match '^RABBITMQ_USERNAME=' } | Select-Object -First 1) -replace '^RABBITMQ_USERNAME=', ''
$vhosts = docker exec $rabbitContainer rabbitmqctl -q list_vhosts name
if ($vhosts -notcontains '/dongqh') {
    docker exec $rabbitContainer rabbitmqctl add_vhost /dongqh
}
docker exec $rabbitContainer rabbitmqctl set_permissions -p /dongqh $rabbitUser '.*' '.*' '.*'
```

- [ ] **Step 3: Verify broker state**

Run RabbitMQ diagnostics, list virtual hosts, and list permissions for `/dongqh`.

Expected: diagnostics return `Ping succeeded`; `/dongqh` is present; the configured user has `.*` for configure, write, and read.

- [ ] **Step 4: Final repository verification**

Run: `python -m pytest tests/test_rabbitmq_infrastructure_contract.py tests/test_infrastructure_contract.py -q`

Expected: all selected tests pass.

Run: `git diff --check`

Expected: no whitespace errors. Review the diff to confirm `.env` is not tracked and no real password appears in committed files.
