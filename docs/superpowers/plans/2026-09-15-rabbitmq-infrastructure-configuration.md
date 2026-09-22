# RabbitMQ Infrastructure Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a healthy, persistent RabbitMQ 4.3.5 container and environment-backed Marketing connection settings without changing the active Redis Stream provider.

**Architecture:** Docker Compose owns the broker lifecycle, credentials, ports, health check, and named data volume. Local secrets live only in `.env`; committed configuration uses environment placeholders. Marketing receives connection settings but remains on `luckyhub.messaging.provider: redis-stream` and has no RabbitMQ runtime dependency in this phase.

**Tech Stack:** Docker Compose, RabbitMQ 4.3.5 management Alpine image, Spring Boot YAML, Python pytest/unittest contracts, PowerShell 7.

## Global Constraints

- Pin the broker image to `rabbitmq:4.3.5-management-alpine`.
- Keep actual credentials only in `.env`.
- Use the existing Compose default network `hmall-lottery_default`.
- Keep `luckyhub.messaging.provider: redis-stream` unchanged.
- Do not add AMQP publishers, consumers, queues, exchanges, bindings, or a Marketing RabbitMQ dependency.
- Do not make Marketing depend on RabbitMQ health in this phase.
- Preserve all unrelated dirty-worktree changes.

---

### Task 1: Add a failing infrastructure contract

**Files:**
- Create: `tests/test_rabbitmq_infrastructure_contract.py`

**Interfaces:**
- Consumes: `compose.yaml`, `.env.example`, and `marketing-service/src/main/resources/application.yaml`.
- Produces: executable assertions for broker topology, safe configuration, and the unchanged Redis Stream provider.

- [ ] **Step 1: Write the failing contract**

Create a unittest that renders Compose with `.env.example` and asserts:

```python
service = compose_config()["services"]["rabbitmq"]
self.assertEqual("rabbitmq:4.3.5-management-alpine", service["image"])
self.assertEqual("rabbitmq", service["hostname"])
self.assertIn("healthcheck", service)
self.assertIn("/var/lib/rabbitmq", {v["target"] for v in service["volumes"]})
self.assertNotIn("rabbitmq", compose_config()["services"]["marketing-service"]["depends_on"])
```

Also assert exact environment-variable names, host/container port mappings, `spring.rabbitmq` placeholders, and `provider: redis-stream`.

- [ ] **Step 2: Verify RED**

Run:

```powershell
python -m pytest tests/test_rabbitmq_infrastructure_contract.py -q
```

Expected: FAIL because the `rabbitmq` Compose service and configuration variables do not exist.

---

### Task 2: Add broker and project connection configuration

**Files:**
- Modify: `compose.yaml`
- Modify: `.env`
- Modify: `.env.example`
- Modify: `marketing-service/src/main/resources/application.yaml`

**Interfaces:**
- Consumes: `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`, `RABBITMQ_PORT`, and `RABBITMQ_MANAGEMENT_PORT`.
- Produces: Compose service `rabbitmq` and inactive Marketing `spring.rabbitmq` connection properties.

- [ ] **Step 1: Add local and example environment variables**

Use local development values in `.env`:

```dotenv
RABBITMQ_USERNAME=hmall_mq_admin
RABBITMQ_PASSWORD=HmallRabbit_2026_Dev
RABBITMQ_VHOST=/hmall
RABBITMQ_PORT=5672
RABBITMQ_MANAGEMENT_PORT=15672
```

Use non-secret replacements in `.env.example` with the same keys.

- [ ] **Step 2: Add the Compose broker**

Add `rabbitmq` with the pinned image, environment-backed default user/password/vhost, host ports, `rabbitmq-data:/var/lib/rabbitmq`, `restart: unless-stopped`, and:

```yaml
healthcheck:
  test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
  interval: 10s
  timeout: 5s
  retries: 20
  start_period: 30s
```

Declare the `rabbitmq-data` named volume. Add RabbitMQ environment variables to Marketing without adding a `depends_on` edge.

- [ ] **Step 3: Add inactive Marketing connection settings**

Add:

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:}
    password: ${RABBITMQ_PASSWORD:}
    virtual-host: ${RABBITMQ_VHOST:/}
```

Do not change `luckyhub.messaging.provider: redis-stream`.

- [ ] **Step 4: Verify GREEN**

Run the Task 1 pytest command and `docker compose --env-file .env.example config --quiet`.

Expected: all contract tests pass and Compose exits 0.

---

### Task 3: Start and verify RabbitMQ

**Files:**
- No source changes expected.

**Interfaces:**
- Consumes: the Compose `rabbitmq` service and local `.env` values.
- Produces: a healthy local broker on ports 5672 and 15672.

- [ ] **Step 1: Pull the image with a user-visible progress update**

Run:

```powershell
docker compose --env-file .env pull rabbitmq
```

If the pull continues asynchronously, report that image layers are still downloading and poll the same command session until completion.

- [ ] **Step 2: Start the broker**

Run:

```powershell
docker compose --env-file .env up -d rabbitmq
```

- [ ] **Step 3: Wait for broker health**

Poll `docker inspect` for `healthy`, then run:

```powershell
docker exec hmall-lottery-rabbitmq-1 rabbitmq-diagnostics -q ping
docker exec hmall-lottery-rabbitmq-1 rabbitmqctl version
docker exec hmall-lottery-rabbitmq-1 rabbitmq-plugins list -e -m
```

Expected: ping succeeds, version is `4.3.5`, and `rabbitmq_management` is enabled.

- [ ] **Step 4: Verify credentials without printing the password**

Read the credentials into PowerShell variables from `.env`, URL-encode `/hmall`, and call the management API endpoint `/api/vhosts/%2Fhmall` with Basic authentication.

Expected: HTTP 200 and vhost name `/hmall`.

- [ ] **Step 5: Run final verification**

Run the RabbitMQ contract, the existing infrastructure contracts, Compose config validation, `git diff --check`, and `docker compose ps rabbitmq`.

Expected: zero test failures, zero configuration errors, zero whitespace errors, and a healthy RabbitMQ container.
