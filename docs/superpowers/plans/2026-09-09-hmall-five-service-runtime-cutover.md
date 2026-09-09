# Hmall Five-Service Runtime Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `item-service`, `cart-service`, `user-service`, `trade-service`, and `pay-service` the default Black Mall runtime without losing any frontend behavior, while keeping the monolith as an explicit rollback profile and preparing the shared contracts for later LuckyHub extraction.

**Architecture:** Nginx owns path routing and sends each Black Mall API prefix directly to its owning service. All five Java 11/Spring Boot 2.7 services register with Nacos and use OpenFeign contracts from the pure Java 11 `hm-api` JAR. Each service connects only to its own MySQL database. `marketing-service` remains Java 17/Spring Boot 4 during this phase and continues to use the current HTTP integration until its three-service extraction begins.

**Tech Stack:** Java 11, Spring Boot 2.7.12, Spring Cloud 2021.0.3, Spring Cloud Alibaba 2021.0.4.0, OpenFeign, Nacos, MyBatis-Plus, MySQL 8.4, Redis 7.4, Docker Compose, Nginx, Python `unittest` contract tests, JUnit 5/Mockito.

## Global Constraints

- Preserve every existing user change in the dirty worktree; stage and commit only the files named by the current task.
- Do not modify or renumber existing SQL migrations `V001` through `V008` or LuckyHub Flyway migrations `V1` through `V25`.
- Keep Black Mall modules on Java 11/Spring Boot 2.7 and `marketing-service` on Java 17/Spring Boot 4.
- Keep `hm-api` free of persistence, Swagger/Knife4j, web-server, and implementation dependencies.
- Do not introduce direct database access across services; all cross-service calls go through `hm-api` OpenFeign clients.
- Keep secrets in environment variables. `.env.example` contains safe placeholders only.
- Run each red test before implementation and record that it failed for the intended reason.
- Because `mall-backend/hm-service/target` predates this plan, do not delete it without user approval; run focused verification separately from the workspace-layout guard.

---

## Task 1: Purify the `hm-api` Contract JAR

**Files:**

- Modify: `hm-api/pom.xml`
- Test: `tests/test_hm_api_contract.py`

- [x] **Step 1: Run the existing contract test and confirm the intended failure**

Run:

```powershell
python -m unittest tests.test_hm_api_contract.HmApiContractTest.test_hm_api_is_a_non_runnable_openfeign_contract_jar -v
```

Expected: FAIL because `hm-api/pom.xml` still contains `knife4j-openapi2-spring-boot-starter`.

- [x] **Step 2: Remove the Knife4j dependency from `hm-api`**

After the edit, the dependencies block must contain only this direct dependency:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
</dependencies>
```

- [x] **Step 3: Verify the full `hm-api` contract**

Run:

```powershell
python -m unittest tests.test_hm_api_contract -v
mvn -B -ntp -pl hm-api -am test
```

Expected: both commands PASS.

- [x] **Step 4: Commit only the contract cleanup**

```powershell
git add -- hm-api/pom.xml
git commit -m "refactor: keep hm-api as pure feign contracts"
```

---

## Task 2: Standardize Runtime Configuration and Secrets

**Files:**

- Modify: `tests/test_user_trade_pay_database_split.py`
- Modify: `user-service/src/main/resources/application-dev.yaml`
- Modify: `user-service/src/main/resources/application-local.yaml`
- Modify: `trade-service/src/main/resources/application-dev.yaml`
- Modify: `trade-service/src/main/resources/application-local.yaml`
- Modify: `pay-service/src/main/resources/application-dev.yaml`
- Modify: `pay-service/src/main/resources/application-local.yaml`
- Modify: `.env.example`
- Modify: `compose.yaml`

- [x] **Step 1: Change the database profile test from literal passwords to environment contracts**

Replace `test_service_profiles_use_isolated_databases_and_literal_passwords` with assertions that each service uses its own variable in both profiles:

```python
def test_service_profiles_use_isolated_databases_and_environment_passwords(self):
    for domain, (database, account, _, _) in self.DATABASES.items():
        variable = f"HM_{domain.upper()}_DB_PASSWORD"
        local = self.read(f"{domain}-service/src/main/resources/application-local.yaml")
        dev = self.read(f"{domain}-service/src/main/resources/application-dev.yaml")
        for content in (local, dev):
            self.assertIn(f"database: {database}", content)
            self.assertIn(f"username: {account}", content)
            self.assertIn(f"${{{variable}:", content)
        self.assertIn("host: 127.0.0.1", local)
        self.assertIn(f"port: ${{{variable.replace('_PASSWORD', '_PORT')}:13307}}", local)
        self.assertIn(f"host: ${{{variable.replace('_PASSWORD', '_HOST')}:mysql}}", dev)
        self.assertIn(f"port: ${{{variable.replace('_PASSWORD', '_PORT')}:3306}}", dev)
        self.assertIn(variable, self.read(".env.example"))
```

Also change `test_compose_runs_all_three_migrations_with_yaml_credentials` so it requires `${HM_*_DB_PASSWORD:?set ... in .env}` rather than the current literal development values.

- [x] **Step 2: Run the focused test and confirm it fails on hard-coded user/trade/pay credentials**

```powershell
python -m unittest tests.test_user_trade_pay_database_split -v
```

Expected: FAIL for user, trade, and pay profile/Compose credentials.

- [x] **Step 3: Parameterize all three service profiles**

Use the following pattern, substituting `USER`, `TRADE`, or `PAY`:

```yaml
hm:
  db:
    host: ${HM_USER_DB_HOST:mysql}
    port: ${HM_USER_DB_PORT:3306}
    database: hm_user
    username: hm_user_app
    password: ${HM_USER_DB_PASSWORD:change-user-db-password}
```

Local profiles must retain both optional `.env` imports and use `${HM_USER_DB_PORT:13307}` with host `127.0.0.1`. Add those imports to trade and pay local profiles, where they are currently absent.

- [x] **Step 4: Add all service credentials and Nacos client credentials to `.env.example`**

```properties
HM_USER_DB_PASSWORD=change-user-db-password
HM_TRADE_DB_PASSWORD=change-trade-db-password
HM_PAY_DB_PASSWORD=change-pay-db-password
NACOS_USERNAME=nacos
NACOS_PASSWORD=nacos
```

In `compose.yaml`, make the schema initializer consume all five database passwords using required environment substitutions.

- [x] **Step 5: Verify profiles and resolved Compose syntax**

```powershell
python -m unittest tests.test_user_trade_pay_database_split -v
docker compose --env-file .env.example -f compose.yaml config --quiet
```

Expected: PASS.

- [x] **Step 6: Commit only configuration standardization**

```powershell
git add -- tests/test_user_trade_pay_database_split.py user-service/src/main/resources/application-dev.yaml user-service/src/main/resources/application-local.yaml trade-service/src/main/resources/application-dev.yaml trade-service/src/main/resources/application-local.yaml pay-service/src/main/resources/application-dev.yaml pay-service/src/main/resources/application-local.yaml .env.example compose.yaml
git commit -m "config: standardize split service environments"
```

---

## Task 3: Restore the Item Search API in `item-service`

**Files:**

- Create: `item-service/src/main/java/com/dongqh/itemservice/controller/SearchController.java`
- Create: `item-service/src/main/java/com/dongqh/itemservice/service/ItemSearchService.java`
- Create: `item-service/src/main/java/com/dongqh/itemservice/service/impl/ItemSearchServiceImpl.java`
- Create: `item-service/src/test/java/com/dongqh/itemservice/service/impl/ItemSearchServiceImplTest.java`
- Create: `tests/test_split_frontend_api_contract.py`

- [x] **Step 1: Add a frontend-to-service ownership contract**

Create `tests/test_split_frontend_api_contract.py` with a helper that reads UTF-8 files and these assertions:

```python
def test_item_service_owns_every_search_route_used_by_frontend(self):
    controller = read("item-service/src/main/java/com/dongqh/itemservice/controller/SearchController.java")
    self.assertIn('@RequestMapping("/search")', controller)
    self.assertIn('@GetMapping("/list")', controller)
    self.assertIn('@GetMapping("/suggestion")', controller)
    self.assertIn('@PostMapping("/filters")', controller)
```

- [x] **Step 2: Add failing Java tests for query behavior**

The test class must mock `IItemService` and verify:

- list search always filters `status = 1` and maps `Page<Item>` to `PageDTO<ItemDTO>`;
- suggestion returns at most 10 distinct nonblank names;
- filters return distinct `category` and `brand` values from active matching items;
- a missing `minPrice` or `maxPrice` never builds a half-null `between` predicate.

Run:

```powershell
python -m unittest tests.test_split_frontend_api_contract -v
mvn -B -ntp -pl item-service -am -Dtest=ItemSearchServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: Python FAIL because the controller is absent; Maven FAIL because the test references an absent service.

- [x] **Step 3: Implement a small search service using the existing item table**

The interface is:

```java
public interface ItemSearchService {
    PageDTO<ItemDTO> search(ItemPageQuery query);
    List<String> suggest(String key);
    Map<String, List<String>> filters(ItemPageQuery query);
}
```

The implementation must use `IItemService.lambdaQuery()`, always apply `Item::getStatus = 1`, use `between` only when both price bounds are non-null, sort through `query.toMpPage("update_time", false)`, and never introduce Elasticsearch or a duplicate catalog store in this phase.

- [x] **Step 4: Expose the three routes from a thin controller**

```java
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {
    private final ItemSearchService itemSearchService;

    @GetMapping("/list")
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        return itemSearchService.search(query);
    }

    @GetMapping("/suggestion")
    public List<String> suggest(@RequestParam String key) {
        return itemSearchService.suggest(key);
    }

    @PostMapping("/filters")
    public Map<String, List<String>> filters(@RequestBody ItemPageQuery query) {
        return itemSearchService.filters(query);
    }
}
```

- [x] **Step 5: Verify search contracts and item-service tests**

```powershell
python -m unittest tests.test_split_frontend_api_contract -v
mvn -B -ntp -pl item-service -am test
```

Expected: PASS.

- [x] **Step 6: Commit the search cutover**

```powershell
git add -- item-service/src/main/java/com/dongqh/itemservice/controller/SearchController.java item-service/src/main/java/com/dongqh/itemservice/service/ItemSearchService.java item-service/src/main/java/com/dongqh/itemservice/service/impl/ItemSearchServiceImpl.java item-service/src/test/java/com/dongqh/itemservice/service/impl/ItemSearchServiceImplTest.java tests/test_split_frontend_api_contract.py
git commit -m "feat: move product search into item service"
```

---

## Task 4: Restore User Administration in `user-service`

**Files:**

- Modify: `tests/test_split_frontend_api_contract.py`
- Create: `user-service/src/main/java/com/dongqh/userservice/domain/dto/UserAdminFormDTO.java`
- Create: `user-service/src/main/java/com/dongqh/userservice/domain/query/UserPageQuery.java`
- Create: `user-service/src/main/java/com/dongqh/userservice/domain/vo/UserAdminVO.java`
- Modify: `user-service/src/main/java/com/dongqh/userservice/controller/UserController.java`
- Modify: `user-service/src/main/java/com/dongqh/userservice/service/IUserService.java`
- Modify: `user-service/src/main/java/com/dongqh/userservice/service/impl/UserServiceImpl.java`
- Create: `user-service/src/test/java/com/dongqh/userservice/service/impl/UserAdminServiceTest.java`

- [x] **Step 1: Extend the route ownership contract**

Add assertions for the exact management routes used by `nginx/html/hmall-admin/users.html`:

```python
def test_user_service_owns_every_user_route_used_by_frontend(self):
    controller = read("user-service/src/main/java/com/dongqh/userservice/controller/UserController.java")
    for route in ('@GetMapping("/page")', '@PostMapping', '@PutMapping',
                  '@DeleteMapping("/{id}")', '@PutMapping("/status/{id}/{status}")'):
        self.assertIn(route, controller)
```

- [x] **Step 2: Write failing service tests**

Test with a mocked `UserMapper`/spy service that:

- paged results never expose password or balance;
- create requires username, phone, and nonblank password, hashes the password with `PasswordEncoder`, defaults status to `NORMAL`, and defaults role to `USER`;
- update ignores a null/blank password and never overwrites the stored hash with blank text;
- status accepts the service-native values `0`/`1` and maps the legacy admin UI value `2` to `FROZEN`;
- every administration operation verifies that the current authenticated user has role `ADMIN`;
- delete rejects the currently authenticated user and deletes exactly one other user.

Run:

```powershell
python -m unittest tests.test_split_frontend_api_contract -v
mvn -B -ntp -pl user-service -am -Dtest=UserAdminServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because DTOs and methods do not yet exist.

- [x] **Step 3: Add purpose-specific DTOs rather than exposing `User`**

`UserAdminFormDTO` fields: `id`, `username`, `password`, `phone`, `role`, `status`.

`UserAdminVO` fields: `id`, `username`, `phone`, `role`, `status`, `createTime`, `updateTime`.

`UserPageQuery` extends `PageQuery` and adds optional `key`, `status`, and `role`.

- [x] **Step 4: Add service operations**

Add these signatures to `IUserService`:

```java
PageDTO<UserAdminVO> queryUsers(UserPageQuery query);
void createUser(UserAdminFormDTO form);
void updateUser(UserAdminFormDTO form);
void deleteUser(Long id);
void updateStatus(Long id, Integer status);
```

Use MyBatis-Plus queries and updates inside `UserServiceImpl`; do not add a second user store or copy these endpoints back into `marketing-service`.

- [x] **Step 5: Add the five controller endpoints**

```java
@GetMapping("/page")
public PageDTO<UserAdminVO> queryUsers(@Valid UserPageQuery query)

@PostMapping
public void createUser(@Valid @RequestBody UserAdminFormDTO form)

@PutMapping
public void updateUser(@Valid @RequestBody UserAdminFormDTO form)

@DeleteMapping("/{id}")
public void deleteUser(@PathVariable Long id)

@PutMapping("/status/{id}/{status}")
public void updateStatus(@PathVariable Long id, @PathVariable Integer status)
```

- [x] **Step 6: Verify the user module**

```powershell
python -m unittest tests.test_split_frontend_api_contract -v
mvn -B -ntp -pl user-service -am test
```

Expected: PASS.

- [x] **Step 7: Commit user administration**

```powershell
git add -- tests/test_split_frontend_api_contract.py user-service/src/main/java/com/dongqh/userservice/domain/dto/UserAdminFormDTO.java user-service/src/main/java/com/dongqh/userservice/domain/query/UserPageQuery.java user-service/src/main/java/com/dongqh/userservice/domain/vo/UserAdminVO.java user-service/src/main/java/com/dongqh/userservice/controller/UserController.java user-service/src/main/java/com/dongqh/userservice/service/IUserService.java user-service/src/main/java/com/dongqh/userservice/service/impl/UserServiceImpl.java user-service/src/test/java/com/dongqh/userservice/service/impl/UserAdminServiceTest.java
git commit -m "feat: move user administration into user service"
```

---

## Task 5: Replace Broken Payment Polling with Owned Status APIs

**Files:**

- Modify: `tests/test_split_frontend_api_contract.py`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/controller/OrderController.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/service/IOrderService.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/service/impl/OrderServiceImpl.java`
- Modify: `pay-service/src/main/java/com/dongqh/payservice/controller/PayController.java`
- Modify: `pay-service/src/main/java/com/dongqh/payservice/service/IPayOrderService.java`
- Modify: `pay-service/src/main/java/com/dongqh/payservice/service/impl/PayOrderServiceImpl.java`
- Modify: `nginx/html/hmall-portal/pay.html`
- Create: `trade-service/src/test/java/com/dongqh/tradeservice/service/impl/OrderStatusQueryTest.java`
- Create: `pay-service/src/test/java/com/dongqh/payservice/service/impl/PayStatusQueryTest.java`

- [x] **Step 1: Add failing frontend/status ownership tests**

Assert that `pay.html` contains neither `"/order/status/" + id` nor `"/pay/status/" + id`, and that it uses `this.order.id` and `this.payOrderNo`. Assert owned routes:

```java
@GetMapping("/{orderId}/status")
@GetMapping("/{payOrderId}/status")
```

under `/orders` and `/pay-orders`, respectively.

- [x] **Step 2: Add service tests for ownership and authorization**

Trade status query must select by both order id and `UserContext.getUser()` and return the order status. Pay status query must select by both pay-order id and `UserContext.getUser()` and return the stored `PayStatus` value; missing records raise the shared `BadRequestException` because this Java 11 baseline does not define a `NotFoundException`.

Run:

```powershell
python -m unittest tests.test_split_frontend_api_contract -v
mvn -B -ntp -pl trade-service,pay-service -am -Dtest=OrderStatusQueryTest,PayStatusQueryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL on absent APIs and the stale frontend paths.

- [x] **Step 3: Implement thin owned status endpoints**

Add `Integer queryOrderStatus(Long orderId)` to `IOrderService` and `Integer queryPayStatus(Long payOrderId)` to `IPayOrderService`. Reuse the existing service databases and user context; do not create an aggregator or expose payment persistence through `hm-api`.

- [x] **Step 4: Correct frontend polling identifiers and paths**

Use:

```javascript
axios.get("/orders/" + this.order.id + "/status")
axios.get("/pay-orders/" + this.payOrderNo + "/status")
location.href = "/paysuccess.html?orderId=" + this.order.id;
```

Balance payment behavior remains unchanged.

- [x] **Step 5: Verify trade, pay, and frontend contracts**

```powershell
python -m unittest tests.test_split_frontend_api_contract -v
mvn -B -ntp -pl trade-service,pay-service -am test
```

Expected: PASS.

- [x] **Step 6: Commit payment status compatibility**

```powershell
git add -- tests/test_split_frontend_api_contract.py trade-service/src/main/java/com/dongqh/tradeservice/controller/OrderController.java trade-service/src/main/java/com/dongqh/tradeservice/service/IOrderService.java trade-service/src/main/java/com/dongqh/tradeservice/service/impl/OrderServiceImpl.java pay-service/src/main/java/com/dongqh/payservice/controller/PayController.java pay-service/src/main/java/com/dongqh/payservice/service/IPayOrderService.java pay-service/src/main/java/com/dongqh/payservice/service/impl/PayOrderServiceImpl.java nginx/html/hmall-portal/pay.html trade-service/src/test/java/com/dongqh/tradeservice/service/impl/OrderStatusQueryTest.java pay-service/src/test/java/com/dongqh/payservice/service/impl/PayStatusQueryTest.java
git commit -m "fix: route payment status through owning services"
```

---

## Task 6: Add Java 11 Container Images for All Five Services

**Files:**

- Modify: `tests/test_infrastructure_contract.py`
- Create: `.dockerignore`
- Create: `item-service/Dockerfile`
- Create: `cart-service/Dockerfile`
- Create: `user-service/Dockerfile`
- Create: `trade-service/Dockerfile`
- Create: `pay-service/Dockerfile`

- [ ] **Step 1: Change the Dockerfile contract to require all five split images**

Replace the monolith-only test with:

```python
def test_split_services_are_built_in_java_11_multistage_images(self):
    for service in ("item", "cart", "user", "trade", "pay"):
        self.assert_multistage_java_build(f"{service}-service/Dockerfile", "11")
```

Add all five Dockerfiles to `RequiredFilesContractTest`.

- [ ] **Step 2: Run the Dockerfile tests and confirm all five files are missing**

```powershell
python -m unittest tests.test_infrastructure_contract.DockerfileContractTest tests.test_infrastructure_contract.RequiredFilesContractTest -v
```

Expected: FAIL listing the five absent Dockerfiles.

- [ ] **Step 3: Exclude build products and create a consistent root-reactor multistage Dockerfile per service**

Create `.dockerignore` first:

```dockerignore
.git
.idea
.pytest_cache
.codex-progress
.superpowers
**/target
**/*.log
.env
```

Each file uses repository root as build context and follows this exact structure, substituting the module and JAR name:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY mall-backend/pom.xml ./mall-backend/pom.xml
COPY mall-backend/hm-common/pom.xml ./mall-backend/hm-common/pom.xml
COPY mall-backend/hm-service/pom.xml ./mall-backend/hm-service/pom.xml
COPY hm-api/pom.xml ./hm-api/pom.xml
COPY item-service/pom.xml ./item-service/pom.xml
COPY cart-service/pom.xml ./cart-service/pom.xml
COPY user-service/pom.xml ./user-service/pom.xml
COPY trade-service/pom.xml ./trade-service/pom.xml
COPY pay-service/pom.xml ./pay-service/pom.xml
COPY marketing-service/pom.xml ./marketing-service/pom.xml
COPY mall-backend/hm-common/src ./mall-backend/hm-common/src
COPY hm-api/src ./hm-api/src
COPY item-service/src ./item-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl item-service -am package -Dmaven.test.skip=true

FROM eclipse-temurin:11-jre
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=build /workspace/item-service/target/item-service.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Do not copy `marketing-service` or the monolith into these images. Ports are 8081 through 8085 in item/cart/user/trade/pay order.

- [ ] **Step 4: Verify contracts and build one representative image before all images**

```powershell
python -m unittest tests.test_infrastructure_contract.DockerfileContractTest tests.test_infrastructure_contract.RequiredFilesContractTest -v
docker build -f item-service/Dockerfile -t hmall-item-service:test .
```

Expected: PASS. If Docker is unavailable, record that limitation and still run `mvn -B -ntp -pl item-service,cart-service,user-service,trade-service,pay-service -am package -DskipTests`.

- [ ] **Step 5: Commit the five images and their contract**

```powershell
git add -- tests/test_infrastructure_contract.py .dockerignore item-service/Dockerfile cart-service/Dockerfile user-service/Dockerfile trade-service/Dockerfile pay-service/Dockerfile
git commit -m "build: containerize five hmall services"
```

---

## Task 7: Cut Compose and Nginx Over to the Five Services

**Files:**

- Modify: `tests/test_infrastructure_contract.py`
- Modify: `compose.yaml`
- Modify: `nginx/nginx.conf`
- Modify: `marketing-service/src/main/resources/application.yaml`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/integration/hmall/HttpHmallCatalogClient.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/integration/hmall/HttpHmallAddressClient.java`

- [ ] **Step 1: Replace the old Compose expectations with split-service expectations**

The expected default service set becomes:

```python
{
    "mysql", "redis", "schema-init", "nacos",
    "item-service", "cart-service", "user-service",
    "trade-service", "pay-service", "marketing-service", "nginx",
}
```

Add assertions that every split service:

- builds from root context `.` using `<name>-service/Dockerfile`;
- sets `SPRING_PROFILES_ACTIVE=dev`;
- sets `NACOS_SERVER_ADDR=nacos:8848`, `NACOS_USERNAME`, and `NACOS_PASSWORD`;
- waits for `schema-init` and healthy Nacos;
- receives only its own `HM_*_DB_PASSWORD`;
- is a dependency of Nginx.

Assert `mall-service` exists only in `docker compose --profile rollback config`, not in the default resolved service set.

- [ ] **Step 2: Replace Nginx's catch-all monolith test with explicit ownership**

Test both portal and admin server blocks for these mappings:

| Prefix | Upstream |
|---|---|
| `/api/items`, `/api/search` | `item-service:8081` |
| `/api/carts` | `cart-service:8082` |
| `/api/users`, `/api/addresses` | `user-service:8083` |
| `/api/orders` | `trade-service:8084` |
| `/api/pay-orders` | `pay-service:8085` |
| `/api/marketing/` | `marketing-service:8080`, with `/marketing` removed |

Also assert there is no default `proxy_pass http://mall-service:8080`.

- [ ] **Step 3: Run the infrastructure tests and confirm they fail against the monolith topology**

```powershell
python -m unittest tests.test_infrastructure_contract.ComposeContractTest tests.test_infrastructure_contract.NginxRoutingContractTest -v
```

Expected: FAIL because Compose and Nginx still use `mall-service`.

- [ ] **Step 4: Define all five services in `compose.yaml`**

Use root build context for each service. Each service gets its database password, Nacos configuration, timezone, and dependency conditions. Services that make Feign calls must reach Nacos by service name. Keep `mall-service` with:

```yaml
profiles: ["rollback"]
```

The default Nginx service must not depend on the rollback-only monolith.

- [ ] **Step 5: Point marketing integration at service-owned endpoints**

Replace `HMALL_BASE_URL: http://mall-service:8080` with the two named base URLs LuckyHub currently calls:

```yaml
HMALL_ITEM_BASE_URL: http://item-service:8081
HMALL_USER_BASE_URL: http://user-service:8083
```

Replace the single `luckyhub.integration.hmall.base-url` property with `catalog-base-url` and `address-base-url`. Change `HttpHmallCatalogClient` to inject `${luckyhub.integration.hmall.catalog-base-url:http://localhost:8081}` and `HttpHmallAddressClient` to inject `${luckyhub.integration.hmall.address-base-url:http://localhost:8083}`. Keep their package-private constructors unchanged so existing unit tests still inject explicit base URLs. Do not migrate these adapters to Feign in this phase; that belongs to the LuckyHub extraction plan.

- [ ] **Step 6: Add explicit Nginx locations in longest-prefix order**

Factor shared proxy headers into an include only if it reduces duplication without changing behavior. Every `/api/...` route strips `/api` before forwarding. Keep the marketing rewrite unchanged.

- [ ] **Step 7: Verify both default and rollback Compose configurations**

```powershell
python -m unittest tests.test_infrastructure_contract -v
docker compose --env-file .env.example -f compose.yaml config --quiet
docker compose --env-file .env.example -f compose.yaml --profile rollback config --quiet
nginx -t -c (Resolve-Path 'nginx/nginx.conf').Path
```

Expected: Python and Compose checks PASS. If host Nginx is unavailable, validate through the image in Task 8 instead of claiming the host command passed.

- [ ] **Step 8: Commit the runtime cutover**

```powershell
git add -- tests/test_infrastructure_contract.py compose.yaml nginx/nginx.conf marketing-service/src/main/resources/application.yaml marketing-service/src/main/java/com/dongqh/luckyhub/integration/hmall/HttpHmallCatalogClient.java marketing-service/src/main/java/com/dongqh/luckyhub/integration/hmall/HttpHmallAddressClient.java
git commit -m "feat: route runtime to split hmall services"
```

---

## Task 8: Build and Smoke-Test the Default Runtime

**Files:**

- Create: `scripts/Smoke-Test.ps1`
- Modify: `README.md`
- Modify: `tests/test_infrastructure_contract.py`

- [ ] **Step 1: Add a failing smoke-script contract**

Require `scripts/Smoke-Test.ps1` to test:

- portal and admin static pages;
- `POST /api/users/login` and captured authorization token;
- `GET /api/search/list`;
- `GET /api/items/{id}` using an id returned by search;
- authenticated `GET /api/carts`, `GET /api/addresses`, and `GET /api/orders/me`;
- authenticated `GET /api/marketing/memberships/me`.

The script must stop on non-2xx responses and must not contain real secrets.

- [ ] **Step 2: Run the contract and confirm the script is absent**

```powershell
python -m unittest tests.test_infrastructure_contract -v
```

Expected: FAIL on the missing smoke script.

- [ ] **Step 3: Implement the PowerShell smoke test**

Parameters:

```powershell
param(
    [string]$PortalBaseUrl = "http://127.0.0.1:18080",
    [string]$AdminBaseUrl = "http://127.0.0.1:18081",
    [string]$Username = "Jack",
    [string]$Password = "123"
)
```

Use `Invoke-RestMethod`, set the `authorization` header from the login response, and print one concise PASS line per boundary.

- [ ] **Step 4: Document default start, rollback start, and verification**

Add to `README.md`:

```powershell
docker compose --env-file .env up --build -d
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Smoke-Test.ps1
docker compose --env-file .env --profile rollback up --build -d mall-service
```

Explain that the first command uses five split services; the rollback profile starts the monolith for diagnosis only and Nginx remains configured for split services unless its config is deliberately reverted.

- [ ] **Step 5: Build all modules and images**

```powershell
mvn -B -ntp -pl hm-api,item-service,cart-service,user-service,trade-service,pay-service -am clean test
docker compose --env-file .env.example -f compose.yaml build item-service cart-service user-service trade-service pay-service marketing-service nginx
```

Expected: PASS.

- [ ] **Step 6: Start the stack and run smoke tests**

Use the real ignored `.env`, not `.env.example`, because Compose rejects placeholder credentials at runtime:

```powershell
docker compose --env-file .env -f compose.yaml up -d
docker compose --env-file .env -f compose.yaml ps
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Smoke-Test.ps1
```

Expected: all containers healthy/running and all smoke boundaries PASS.

- [ ] **Step 7: Run the focused Python regression suite**

```powershell
python -m unittest tests.test_hm_api_contract tests.test_split_frontend_api_contract tests.test_infrastructure_contract tests.test_user_trade_pay_database_split tests.test_trade_payment_order_boundary tests.test_pay_order_business_boundary -v
```

Expected: PASS. Then run `python -m pytest tests -q`; if only the pre-existing `mall-backend/hm-service/target` layout guard fails, report it explicitly rather than deleting user data or claiming a completely green suite.

- [ ] **Step 8: Commit smoke coverage and documentation**

```powershell
git add -- scripts/Smoke-Test.ps1 README.md tests/test_infrastructure_contract.py
git commit -m "test: smoke test split hmall runtime"
```

---

## Completion Gate

- [ ] `hm-api` has only OpenFeign as a direct dependency.
- [ ] All five split services compile and pass their tests on Java 11/Spring Boot 2.7.
- [ ] Search, admin user CRUD, address, cart, order, and payment flows are owned by the correct service.
- [ ] Default Compose contains the five Black Mall services; the monolith is rollback-only.
- [ ] Nginx has no default catch-all route to the monolith.
- [ ] Marketing remains Java 17/Spring Boot 4 and can call the new Black Mall service endpoints.
- [ ] The end-to-end smoke script passes against the running default stack.
- [ ] No existing migration was edited and no unrelated dirty-worktree file was staged.

After this gate, proceed to the next plan in the approved sequence: create `luckyhub-common`, establish Java 17/Boot 4 service conventions, and extract `lottery-service` first.
