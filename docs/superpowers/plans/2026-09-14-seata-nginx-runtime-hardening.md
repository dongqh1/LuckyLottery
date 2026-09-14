# Seata TC and Nginx Runtime Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate Seata's MySQL 8.4 authentication errors and keep Nginx serving static content when Docker upstream services are temporarily absent.

**Architecture:** Keep MySQL's `caching_sha2_password` authentication and make the Seata JDBC client explicitly retrieve the server public key on the trusted Compose network. Defer Nginx upstream lookup to request time through Docker DNS, and add a static-endpoint healthcheck that does not depend on application upstreams.

**Tech Stack:** Docker Compose, Apache Seata 2.4.0, MySQL 8.4, Nginx 1.27 Alpine, Python `unittest`/pytest contract tests, PowerShell 7.

## Global Constraints

- Preserve `seata_app`, its `caching_sha2_password` plugin, and its least-privilege grants.
- Keep all services on `hmall-lottery_default`; do not touch `hm-net`.
- Do not introduce Seata clients, `undo_log`, transaction groups, or `@GlobalTransactional`.
- Do not modify Marketing business logic; its recorded exit 143 was a graceful SIGTERM.
- Preserve all unrelated dirty and staged workspace changes.
- Do not create implementation commits for shared dirty files unless their unrelated hunks can be isolated safely.

---

### Task 1: Make Seata Compatible with MySQL 8.4 Authentication

**Files:**
- Modify: `tests/test_seata_infrastructure_contract.py`
- Modify: `seata/application.yml:85`
- Modify: `mysql/seata/seata-tc.sql`
- Modify: `README.md`

**Interfaces:**
- Consumes: MySQL account `seata_app@%` using `caching_sha2_password`.
- Produces: JDBC URL with `allowPublicKeyRetrieval=true` and `useSSL=false`, plus Seata 2.4 `vgroup_table` storage.

- [ ] **Step 1: Add the failing JDBC authentication contract**

In `SeataRuntimeConfigurationContractTest.test_application_uses_container_dns_and_environment_secrets`, add:

```python
self.assertIn("allowPublicKeyRetrieval=true", application)
self.assertIn("useSSL=false", application)
self.assertIn("vgroup-table: vgroup_table", application)
```

Also add `vgroup_table` to the expected idempotent TC table list and README documentation contract.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
python -m pytest tests/test_seata_infrastructure_contract.py::SeataRuntimeConfigurationContractTest -q
```

Expected: FAIL because the current JDBC URL does not contain `allowPublicKeyRetrieval=true`.

- [ ] **Step 3: Apply the minimal JDBC fix**

Change the active URL in `seata/application.yml` to:

```yaml
url: jdbc:mysql://mysql:3306/seata?rewriteBatchedStatements=true&serverTimezone=Asia%2FShanghai&allowPublicKeyRetrieval=true&useSSL=false
```

Add `vgroup-table: vgroup_table` beside the other Seata DB table mappings, and add the official Seata 2.4 idempotent `vgroup_table` definition to `mysql/seata/seata-tc.sql`. Document all five server tables in README.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run:

```powershell
python -m pytest tests/test_seata_infrastructure_contract.py -q
docker compose --env-file .env config --quiet
```

Expected: all Seata contracts pass and Compose validation exits 0.

---

### Task 2: Make Nginx Independent of Upstream Startup DNS

**Files:**
- Modify: `tests/test_infrastructure_contract.py`
- Modify: `nginx/nginx.conf`
- Modify: `compose.yaml`

**Interfaces:**
- Consumes: Docker DNS resolver `127.0.0.11`, service names `marketing-service` and `hm-gateway`.
- Produces: request-time upstream resolution and a static-only Nginx healthcheck.

- [ ] **Step 1: Add failing Nginx runtime-DNS contracts**

In `NginxRoutingContractTest`, add:

```python
def test_proxy_upstreams_use_docker_runtime_dns(self):
    self.assertRegex(self.conf, r"resolver\s+127\.0\.0\.11\s+valid=10s\s+ipv6=off;")
    self.assertEqual(2, len(re.findall(r"set\s+\$marketing_backend\s+marketing-service:8080;", self.conf)))
    self.assertEqual(2, len(re.findall(r"proxy_pass\s+http://\$marketing_backend;", self.conf)))
    self.assertEqual(2, len(re.findall(r"set\s+\$gateway_backend\s+hm-gateway:8080;", self.conf)))
    self.assertEqual(2, len(re.findall(r"proxy_pass\s+http://\$gateway_backend;", self.conf)))
```

In `ComposeContractTest.test_nginx_exposes_portal_and_admin`, add:

```python
self.assertIn("healthcheck", nginx)
healthcheck = " ".join(nginx["healthcheck"]["test"])
self.assertIn("127.0.0.1:18080", healthcheck)
self.assertIn("127.0.0.1:18081", healthcheck)
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```powershell
python -m pytest tests/test_infrastructure_contract.py::ComposeContractTest::test_nginx_exposes_portal_and_admin tests/test_infrastructure_contract.py::NginxRoutingContractTest -q
```

Expected: FAIL because runtime DNS variables and the Nginx healthcheck are absent.

- [ ] **Step 3: Add Docker runtime DNS and deferred upstreams**

Inside the Nginx `http` block add:

```nginx
resolver 127.0.0.11 valid=10s ipv6=off;
```

In both Portal and Admin Marketing locations replace the static target with:

```nginx
set $marketing_backend marketing-service:8080;
proxy_pass http://$marketing_backend;
```

In both Portal and Admin general API locations replace the static target with:

```nginx
set $gateway_backend hm-gateway:8080;
proxy_pass http://$gateway_backend;
```

- [ ] **Step 4: Add the static Nginx healthcheck**

Under the Compose `nginx` service add:

```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:18080/ && wget -q -O /dev/null http://127.0.0.1:18081/items.html"]
  interval: 10s
  timeout: 5s
  retries: 6
  start_period: 10s
```

- [ ] **Step 5: Run focused tests and validate the rendered configuration**

Run:

```powershell
python -m pytest tests/test_infrastructure_contract.py::ComposeContractTest::test_nginx_exposes_portal_and_admin tests/test_infrastructure_contract.py::NginxRoutingContractTest -q
docker compose --env-file .env config --quiet
```

Expected: all focused tests pass and Compose validation exits 0.

---

### Task 3: Rebuild and Verify Both Runtime Fixes

**Files:**
- Verify only: `compose.yaml`
- Verify only: `.env`
- Verify only: `seata/application.yml`
- Verify only: `nginx/nginx.conf`

**Interfaces:**
- Consumes: running MySQL, Nacos, Redis, Gateway, split services, and the Task 1–2 configuration.
- Produces: healthy Seata and Nginx containers with verified failure isolation and recovery.

- [ ] **Step 1: Recreate Seata and bound the log check to the new container**

Run:

```powershell
docker compose --env-file .env up -d --build --force-recreate seata-server
docker compose --env-file .env ps seata-server
docker logs --since 5m hmall-lottery-seata-server-1
```

Expected: Seata becomes healthy and the fresh logs contain no `Public Key Retrieval is not allowed`, `create connection SQLException`, or `PropertyClass for prefix: [seata.store.db.] should not be null`.

- [ ] **Step 2: Verify Seata DB access and Nacos registration**

Read secrets from `.env` into PowerShell variables without printing them. Query `information_schema.PROCESSLIST` for active `seata_app` connections, query the five TC tables, and inspect `/home/nacos/logs/naming-server.log` for the current `seata-server:8091` registration.

Expected: active `seata_app` connections exist, all five tables exist, at least the four idempotent distributed-lock seeds exist (Seata may add runtime locks), and Nacos records the current Seata instance.

- [ ] **Step 3: Verify Nginx starts while Marketing is absent**

Keep the currently stopped Marketing container stopped, then run:

```powershell
docker compose --env-file .env up -d --build --no-deps --force-recreate nginx
docker compose --env-file .env ps nginx
```

Resolve the actual published ports from the running container because `.env` may override the defaults. Request the Portal root and Admin `/items.html`; both must return 200. Request a Marketing API path; it must return 502 while Nginx remains healthy.

- [ ] **Step 4: Restore Marketing and verify automatic proxy recovery**

Run:

```powershell
docker compose --env-file .env up -d marketing-service
docker compose --env-file .env ps marketing-service nginx
```

Wait for Marketing to become healthy, then request a known Marketing API route through Nginx. The response may be 2xx, 4xx authorization, or 404 depending on the route, but must not be 502. Do not recreate Nginx between the failure and recovery checks.

- [ ] **Step 5: Run full focused regression**

Run:

```powershell
python -m pytest tests/test_seata_infrastructure_contract.py tests/test_infrastructure_contract.py tests/test_nacos_shared_config_contract.py tests/test_frontend_contract.py -q
docker compose --env-file .env config --quiet
```

Expected: zero failures and Compose validation exits 0.

- [ ] **Step 6: Record final status without committing unrelated changes**

Run:

```powershell
git diff --check
git status --short
docker compose --env-file .env ps seata-server marketing-service nginx
```

Expected: all three services are healthy; only intended configuration/test additions plus pre-existing unrelated changes remain uncommitted.
