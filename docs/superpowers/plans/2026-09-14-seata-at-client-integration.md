# Seata AT Client Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate six database-owning microservices with Seata 2.5.0 AT transactions and verify dependency, configuration, XID propagation, and rollback schema contracts.

**Architecture:** Spring Cloud Alibaba's Seata starter supplies TM/RM integration and Feign XID propagation. A small HTTP-client auto-configuration propagates XID for existing RestTemplate/RestClient calls; four synchronous orchestration methods create global transactions. Every physical business database owns an `undo_log` table.

**Tech Stack:** Java 17, Spring Boot 4.0.8, Spring Cloud 2025.1.0, Spring Cloud Alibaba 2025.1.0.0, Apache Seata 2.5.0 AT, Nacos 3.1.1, MySQL 8.4, Maven, Python unittest/pytest, Docker Compose.

## Global Constraints

- Keep Seata server and client at exactly 2.5.0.
- Use transaction group `hmall_tx_group`, mapped to Nacos cluster `default` and service `seata-server`.
- Integrate only `item-service`, `cart-service`, `user-service`, `trade-service`, `pay-service`, and `marketing-service`; do not add Seata to `hm-gateway` or `hm-api`.
- AT protects JDBC writes only; Redis, streams, Outbox, schedulers, and external systems retain existing recovery mechanisms.
- Preserve unrelated dirty-worktree changes and use PowerShell 7/UTF-8.

---

### Task 1: Add a failing Seata integration contract

**Files:**
- Create: `tests/test_seata_client_integration_contract.py`

**Interfaces:**
- Consumes: repository POM, YAML, SQL, Compose, and Java source files.
- Produces: executable contract covering all six clients and four TM entrypoints.

- [ ] **Step 1: Write the failing contract test**

Create tests that assert: root property `seata.version` is `2.5.0`; six POMs contain `spring-cloud-starter-alibaba-seata`; six application YAML files contain `hmall_tx_group`, AT proxy, Nacos registry, `seata-server`, and `default`; five schema-init migrations plus a new LuckyHub Flyway migration create `undo_log`; Compose uses the 2.5.0 server image and makes six services depend on healthy `seata-server`; `createOrder`, `applyCheckoutCoupon`, `cancelOrder`, and `tryPayOrderByBalance` carry `@GlobalTransactional`.

- [ ] **Step 2: Run the contract and verify RED**

Run: `python -m pytest tests/test_seata_client_integration_contract.py -q`

Expected: FAIL because client dependencies/configuration, business `undo_log`, Compose dependencies, and global annotations are absent.

---

### Task 2: Align Seata dependencies, TC runtime, and client configuration

**Files:**
- Modify: `pom.xml`
- Modify: `item-service/pom.xml`
- Modify: `cart-service/pom.xml`
- Modify: `user-service/pom.xml`
- Modify: `trade-service/pom.xml`
- Modify: `pay-service/pom.xml`
- Modify: `marketing-service/pom.xml`
- Modify: six `src/main/resources/application.yaml` files
- Modify: `seata/Dockerfile`
- Modify: `seata/application.yml`
- Modify: `compose.yaml`
- Modify: `.env.example`
- Modify: `README.md`

**Interfaces:**
- Consumes: `NACOS_SERVER_ADDR`, `NACOS_USERNAME`, `NACOS_PASSWORD`, `SEATA_ENABLED`.
- Produces: six TM/RM clients registered as `hmall_tx_group` and a matching Seata 2.5.0 TC.

- [ ] **Step 1: Add managed and module dependencies**

Add `<seata.version>2.5.0</seata.version>` to root properties, directly manage `org.apache.seata:seata-spring-boot-starter:${seata.version}`, and add this dependency to all six service POMs:

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
</dependency>
```

- [ ] **Step 2: Add identical client settings to all six applications**

Use the following values, adapting only the existing YAML location:

```yaml
seata:
  enabled: ${SEATA_ENABLED:true}
  application-id: ${spring.application.name}
  tx-service-group: hmall_tx_group
  enable-auto-data-source-proxy: true
  data-source-proxy-mode: AT
  service:
    vgroup-mapping:
      hmall_tx_group: default
  registry:
    type: nacos
    nacos:
      application: seata-server
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:18848}
      group: DEFAULT_GROUP
      namespace: ""
      cluster: default
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
```

- [ ] **Step 3: Upgrade TC and Compose wiring**

Pin Docker base/image to `2.5.0.jdk17`, keep the 2.5 runtime in non-Web mode, expose TC RPC on container port 8091, check health on 8091, add `SEATA_ENABLED=true` and Nacos credentials to six services, and add healthy `seata-server` dependencies without creating service dependency cycles.

- [ ] **Step 4: Update operator documentation**

Document 2.5.0, unified port 8091, six client participants, `SEATA_ENABLED`, and the AT database-only boundary.

- [ ] **Step 5: Run the dependency/configuration subset**

Run: `python -m pytest tests/test_seata_client_integration_contract.py -q`

Expected: only undo-log, XID propagation, or annotation assertions remain failing.

---

### Task 3: Provision all AT undo logs

**Files:**
- Modify: `mysql/migrations/V004__create_item_service_database.sql`
- Modify: `mysql/migrations/V005__create_cart_service_database.sql`
- Modify: `mysql/migrations/V006__create_user_service_database.sql`
- Modify: `mysql/migrations/V007__create_trade_service_database.sql`
- Modify: `mysql/migrations/V008__create_pay_service_database.sql`
- Create: `marketing-service/src/main/resources/db/migration/V27__add_seata_undo_log.sql`

**Interfaces:**
- Consumes: Seata AT data-source proxy writes.
- Produces: `undo_log` with unique `(xid, branch_id)` and `ix_log_created` in all six schemas.

- [ ] **Step 1: Add the standard idempotent table to each schema-init migration**

Use schema-qualified tables for the five HMall databases:

```sql
CREATE TABLE IF NOT EXISTS `<schema>`.`undo_log` (
  `branch_id` BIGINT NOT NULL,
  `xid` VARCHAR(128) NOT NULL,
  `context` VARCHAR(128) NOT NULL,
  `rollback_info` LONGBLOB NOT NULL,
  `log_status` INT NOT NULL,
  `log_created` DATETIME(6) NOT NULL,
  `log_modified` DATETIME(6) NOT NULL,
  UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`),
  KEY `ix_log_created` (`log_created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT transaction mode undo table';
```

- [ ] **Step 2: Add the LuckyHub Flyway migration**

Create the same unqualified `undo_log` table in V27 so Flyway owns it in `luckyhub`.

- [ ] **Step 3: Run the SQL contract subset**

Run: `python -m pytest tests/test_seata_client_integration_contract.py -q`

Expected: undo-log assertions PASS.

---

### Task 4: Propagate XID through non-Feign HTTP clients

**Files:**
- Create: `mall-backend/hm-common/src/main/java/com/hmall/common/config/SeataHttpClientAutoConfiguration.java`
- Modify: `mall-backend/hm-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `mall-backend/hm-common/src/test/java/com/hmall/common/config/SeataHttpClientAutoConfigurationTest.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/config/SeataHttpClientConfiguration.java`
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/config/SeataHttpClientConfigurationTests.java`
- Modify: `marketing-service/.../HttpHmallCatalogClient.java`
- Modify: `marketing-service/.../HttpHmallAddressClient.java`

**Interfaces:**
- Consumes: `RootContext.getXID()`.
- Produces: Boot customizers that add `RootContext.KEY_XID` only when an XID is bound.

- [ ] **Step 1: Write Java tests and verify RED**

Bind `RootContext.bind("test-xid")`, execute requests through customized `RestTemplate` and `RestClient`, and assert the mock server receives the XID header; unbind in `finally` and assert ordinary calls omit it.

Run: `mvn -pl mall-backend/hm-common,marketing-service -am -Dtest=SeataHttpClientAutoConfigurationTest,SeataHttpClientConfigurationTests test`

Expected: FAIL because the configuration classes do not exist.

- [ ] **Step 2: Implement request customizers**

Each interceptor reads the XID at request time and executes:

```java
String xid = RootContext.getXID();
if (StringUtils.hasText(xid)) {
    request.getHeaders().set(RootContext.KEY_XID, xid);
}
return execution.execute(request, body);
```

Register it with both `RestTemplateCustomizer` and `RestClientCustomizer`. Change the two LuckyHub production constructors to inject Boot's customized `RestClient.Builder` instead of calling `RestClient.builder()` directly.

- [ ] **Step 3: Run the Java tests and verify GREEN**

Run the Maven command from Step 1.

Expected: PASS with zero failures.

---

### Task 5: Establish global transaction boundaries and verify the build

**Files:**
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/service/impl/OrderServiceImpl.java`
- Modify: `pay-service/src/main/java/com/dongqh/payservice/service/impl/PayOrderServiceImpl.java`
- Modify: relevant trade/pay tests

**Interfaces:**
- Consumes: propagated XID and participating AT data sources.
- Produces: four named global transactions; manual coupon release runs only outside a global transaction.

- [ ] **Step 1: Extend tests and verify RED**

Assert source/reflection metadata for four global entrypoints and exercise the coupon failure path with and without a bound XID, expecting manual release only without an XID.

- [ ] **Step 2: Add global annotations and compensation guard**

Add `@GlobalTransactional(name = "...")` to `createOrder`, `applyCheckoutCoupon`, `cancelOrder`, and `tryPayOrderByBalance`. Guard legacy release callbacks with `!RootContext.inGlobalTransaction()` while retaining local `@Transactional`.

- [ ] **Step 3: Run focused and full contracts**

Run:

```powershell
python -m pytest tests/test_seata_client_integration_contract.py tests/test_seata_infrastructure_contract.py tests/test_infrastructure_contract.py -q
mvn -pl item-service,cart-service,user-service,trade-service,pay-service,marketing-service -am test
mvn -pl item-service,cart-service,user-service,trade-service,pay-service,marketing-service -am dependency:tree -Dincludes=org.apache.seata
docker compose --env-file .env.example config --quiet
```

Expected: all tests/builds exit 0; dependency tree contains only Seata 2.5.0; Compose validates.

- [ ] **Step 4: Run runtime verification when Docker is available**

Rebuild/start `schema-init`, `seata-server`, and six services; verify Nacos registration and force an order/payment branch failure. Query all six schemas for rollback and cleaned `undo_log`. If the runtime stack cannot be safely started, record that limitation explicitly instead of claiming it passed.
