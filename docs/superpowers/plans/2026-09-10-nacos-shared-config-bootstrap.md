# Nacos Shared Configuration Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the five split HMall services load JDBC, logging, and Knife4j configuration from the existing Nacos shared Data IDs during Bootstrap startup.

**Architecture:** Each service adds the Nacos Config and Spring Cloud Bootstrap starters and owns a small `bootstrap.yaml` containing its service name plus the common Nacos connection and `shared-configs` list. Service `application.yaml` files retain only service-specific properties, while profile files continue to supply isolated `hm.db` values. The Nacos logging Data ID is updated once so its package-level logger covers all five `com.dongqh` services.

**Tech Stack:** Java 11, Spring Boot 2.7.12, Spring Cloud 2021.0.3, Spring Cloud Alibaba 2021.0.4.0, Nacos 2.2.3, Maven, Python `unittest`, Docker Compose.

## Global Constraints

- Modify only `item-service`, `cart-service`, `user-service`, `trade-service`, `pay-service`, the new configuration contract test, and Nacos `shared.logger.yaml`.
- Do not modify `hm-gateway`, `marketing-service`, or `mall-backend/hm-service`.
- Preserve all existing user changes and the already-staged `mall-backend/hm-service/src/main/resources/mapper/UserClient.xml`.
- Never print `.env` contents, Nacos access tokens, or database passwords.
- Keep Nacos in the public namespace and use `DEFAULT_GROUP`.
- Keep per-service database names and credentials in the existing local/dev profile files.
- Do not commit implementation files because the service trees contain pre-existing untracked user work; report the working-tree diff instead.

---

### Task 1: Add a failing Nacos configuration contract

**Files:**
- Create: `tests/test_nacos_shared_config_contract.py`
- Modify: `tests/test_infrastructure_contract.py`
- Test: `tests/test_nacos_shared_config_contract.py`

**Interfaces:**
- Consumes: the five service POMs and YAML resource trees.
- Produces: a structural contract describing the required dependencies, Bootstrap properties, shared Data IDs, and local/remote property boundary.

- [ ] **Step 1: Write the failing test**

Create a `unittest` contract with the service/name/package/title table and assertions that:

```python
SERVICES = {
    "item-service": ("com.dongqh.itemservice.controller", "黑马商城商品管理接口文档"),
    "cart-service": ("com.dongqh.cartservice.controller", "黑马商城购物车管理接口文档"),
    "user-service": ("com.dongqh.userservice.controller", "黑马商城用户管理接口文档"),
    "trade-service": ("com.dongqh.tradeservice.controller", "黑马商城交易管理接口文档"),
    "pay-service": ("com.dongqh.payservice.controller", "黑马商城支付管理接口文档"),
}
SHARED_DATA_IDS = ("shared.jdbc.yaml", "shared.logger.yaml", "shared.swagger.yaml")
```

For every service, parse `pom.xml` with `xml.etree.ElementTree` and require both `spring-cloud-starter-alibaba-nacos-config` and `spring-cloud-starter-bootstrap`. Read `bootstrap.yaml` and require the exact service name, `SPRING_PROFILES_ACTIVE`, `NACOS_SERVER_ADDR`, `NACOS_USERNAME`, `NACOS_PASSWORD`, all three Data IDs, `DEFAULT_GROUP`, and one `refresh: true` per Data ID. Read `application.yaml` and reject top-level `mybatis-plus`, `logging`, and `knife4j` as well as `spring.datasource`; require the service-specific `hm.swagger` values. Read both profile files and require `hm.db` plus the service-specific database name.

- [ ] **Step 2: Run the test to verify RED**

Run:

```powershell
python -m unittest tests.test_nacos_shared_config_contract -v
```

Expected: FAIL because the five Bootstrap files and two dependencies do not yet exist and duplicate shared configuration remains in `application.yaml`.

- [ ] **Step 3: Confirm the failure is behavioral**

Verify the failure names a missing `bootstrap.yaml`, missing dependency, or forbidden duplicated key. Fix only test syntax/path mistakes; do not change service configuration yet.

---

### Task 2: Wire all five services to Bootstrap and Nacos shared configuration

**Files:**
- Modify: `item-service/pom.xml`
- Modify: `cart-service/pom.xml`
- Modify: `user-service/pom.xml`
- Modify: `trade-service/pom.xml`
- Modify: `pay-service/pom.xml`
- Create: `item-service/src/main/resources/bootstrap.yaml`
- Create: `cart-service/src/main/resources/bootstrap.yaml`
- Create: `user-service/src/main/resources/bootstrap.yaml`
- Create: `trade-service/src/main/resources/bootstrap.yaml`
- Create: `pay-service/src/main/resources/bootstrap.yaml`
- Modify: `item-service/src/main/resources/application.yaml`
- Modify: `cart-service/src/main/resources/application.yaml`
- Modify: `user-service/src/main/resources/application.yaml`
- Modify: `trade-service/src/main/resources/application.yaml`
- Modify: `pay-service/src/main/resources/application.yaml`
- Modify: `cart-service/src/test/java/com/dongqh/cartservice/CartServiceApplicationTests.java`
- Modify: `user-service/src/test/java/com/dongqh/userservice/UserServiceApplicationTests.java`
- Modify: `trade-service/src/test/java/com/dongqh/tradeservice/TradeServiceApplicationTests.java`
- Modify: `pay-service/src/test/java/com/dongqh/payservice/PayServiceApplicationTests.java`
- Test: `tests/test_nacos_shared_config_contract.py`

**Interfaces:**
- Consumes: public namespace / `DEFAULT_GROUP` Data IDs `shared.jdbc.yaml`, `shared.logger.yaml`, and `shared.swagger.yaml`.
- Produces: five Bootstrap-enabled services whose local `hm.db` and `hm.swagger` properties satisfy placeholders in shared Nacos YAML.

- [ ] **Step 1: Add managed client dependencies**

Immediately after each existing Nacos Discovery dependency add:

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
</dependency>
```

Do not add explicit versions because both are managed by the existing Spring Cloud/Spring Cloud Alibaba BOMs.

- [ ] **Step 2: Create each Bootstrap file**

Create the following structure, substituting the exact service name in each file:

```yaml
spring:
  application:
    name: item-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:18848}
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      config:
        file-extension: yaml
        group: DEFAULT_GROUP
        shared-configs:
          - data-id: shared.jdbc.yaml
            group: DEFAULT_GROUP
            refresh: true
          - data-id: shared.logger.yaml
            group: DEFAULT_GROUP
            refresh: true
          - data-id: shared.swagger.yaml
            group: DEFAULT_GROUP
            refresh: true
```

- [ ] **Step 3: Remove duplicated shared properties**

From each `application.yaml`, remove `spring.application`, `spring.profiles`, `spring.datasource`, top-level `mybatis-plus`, top-level `logging`, and top-level `knife4j`. Keep `server.port`, `spring.cloud.nacos.discovery`, Feign, authentication, JWT, and marketing integration properties.

- [ ] **Step 4: Add exact service-level Swagger placeholders**

Add or merge:

```yaml
hm:
  swagger:
    title: 黑马商城商品管理接口文档
    description: 黑马商城商品管理接口文档
    package: com.dongqh.itemservice.controller
```

Use the service table from Task 1 for the other four services. Do not duplicate the existing `hm` mapping in user/trade/pay; merge `swagger` beside existing `marketing`, `jwt`, and `auth` keys.

- [ ] **Step 5: Run the contract to verify GREEN**

Update the item database isolation assertions so they require `shared.jdbc.yaml` and the default `local` profile in `bootstrap.yaml`, while continuing to require `hm_item`, `hm_item_app`, and `HM_ITEM_DB_PASSWORD` in the service profile/environment files. The test must also reject a locally duplicated `spring.datasource` block.

Set `spring.cloud.nacos.config.enabled=false` in the cart/user/trade/pay `@SpringBootTest` declarations. The cart test keeps its explicit H2 datasource; the other three use their existing test-scoped H2 dependency. Keep the item Swagger test connected to Nacos Config so it verifies the remote shared template.

Run:

```powershell
python -m unittest tests.test_nacos_shared_config_contract -v
```

Expected: all Nacos shared configuration contract tests PASS.

- [ ] **Step 6: Validate Maven dependency resolution**

Run:

```powershell
mvn -pl item-service,cart-service,user-service,trade-service,pay-service -am dependency:tree -Dincludes=com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config,org.springframework.cloud:spring-cloud-starter-bootstrap
```

Expected: BUILD SUCCESS and both dependencies appear for each selected service.

---

### Task 3: Correct and verify the shared Nacos logging and Swagger configuration

**Files:**
- Modify externally: Nacos public namespace, `DEFAULT_GROUP`, `shared.logger.yaml`
- Modify externally: Nacos public namespace, `DEFAULT_GROUP`, `shared.swagger.yaml`
- Test: authenticated Nacos Config API read-back

**Interfaces:**
- Consumes: the current published `shared.logger.yaml` and credentials from ignored `.env`.
- Produces: a logger shared across `com.dongqh` and an ASCII-only Swagger template whose title/description values come from each service's `hm.swagger` properties.

- [ ] **Step 1: Read and validate the current remote value**

Authenticate through `/nacos/v1/auth/login`, retrieve `/nacos/v1/cs/configs`, and assert the content contains exactly one `com.dongqh.payservice: debug` and does not already contain `com.dongqh: debug`. Never output the token or credentials.

- [ ] **Step 2: Publish the minimal replacement**

Replace only the exact logger key in memory and POST the original content with that one replacement to `/nacos/v1/cs/configs` using `dataId=shared.logger.yaml`, `group=DEFAULT_GROUP`, and `type=yaml`.

Expected response: `true`.

- [ ] **Step 3: Read back and verify**

Fetch the Data ID again and require `com.dongqh: debug`, reject `com.dongqh.payservice: debug`, and confirm the date format plus `logs/${spring.application.name}` path are unchanged.

- [ ] **Step 4: Remove non-ASCII defaults from the shared Swagger template**

Read `shared.swagger.yaml`, require exactly one `${hm.swagger.title:黑马商城接口文档}` and one `${hm.swagger.description:黑马商城接口文档描述}`, then replace them with `${hm.swagger.title}` and `${hm.swagger.description}` respectively. Publish as YAML, read back, require exact normalized content equality, and assert every character is ASCII. This works around the 2021.0.4.0 parser's use of platform-default `String.getBytes()` on Windows while preserving the Chinese values supplied by each service.

---

### Task 4: Run regression and live integration verification

**Files:**
- Verify only: five service source trees, `compose.yaml`, live Nacos and Docker containers.

**Interfaces:**
- Consumes: the Bootstrap-enabled service JARs and corrected Nacos Data ID.
- Produces: evidence that static contracts, Maven tests, remote loading, service health, and registration all succeed.

- [ ] **Step 1: Run Python contracts**

Run:

```powershell
python -m unittest tests.test_nacos_shared_config_contract tests.test_infrastructure_contract tests.test_workspace_layout -v
```

Expected: PASS with no failures or errors.

- [ ] **Step 2: Run the five-service Maven test reactor**

Run:

```powershell
mvn -pl item-service,cart-service,user-service,trade-service,pay-service -am test
```

Expected: BUILD SUCCESS. If a context test exposes a pre-existing external database dependency, preserve the production configuration and document the exact test-only failure rather than weakening Nacos loading.

- [ ] **Step 3: Rebuild and recreate the five services**

Run:

```powershell
docker compose up -d --build item-service cart-service user-service trade-service pay-service
```

Expected: all five containers are running.

- [ ] **Step 4: Verify Bootstrap property sources**

Inspect fresh logs for each container and require evidence that all three shared Data IDs were located. Reject authentication, connection, placeholder resolution, and datasource initialization errors.

- [ ] **Step 5: Verify Nacos registration**

Use the authenticated Nacos naming API to query `item-service`, `cart-service`, `user-service`, `trade-service`, and `pay-service` in `DEFAULT_GROUP`; require at least one healthy instance for each.

- [ ] **Step 6: Review only scoped diffs**

Run `git diff --check` and `git status --short`. Inspect diffs only for the test, five POMs, five Bootstrap files, and five application files. Confirm the pre-existing staged `UserClient.xml` remains staged and unchanged by this work.

- [ ] **Step 7: Produce the handoff**

List every changed project file grouped by purpose, describe the remote Nacos change separately, include verification commands/results, and explicitly state any remaining warnings or test limitations.
