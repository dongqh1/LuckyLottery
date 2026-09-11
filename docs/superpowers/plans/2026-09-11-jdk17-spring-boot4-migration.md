# JDK 17 and Spring Boot 4.0 Repository Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade every Java module to JDK 17 and Spring Boot 4.0.8 without changing HTTP contracts, business behavior, service names, ports, routes, data, or local frontend integration.

**Architecture:** Make the root POM the single platform parent, keep existing Maven coordinates and module boundaries, and migrate infrastructure in testable layers: build platform, Jakarta/runtime APIs, documentation, Config Data/Nacos, Gateway, then Docker/runtime. Preserve behavior with contract tests before each structural change and with HTTP smoke tests after the stack starts.

**Tech Stack:** Java 17, Maven 3.6.3+, Spring Boot 4.0.8, Spring Cloud 2025.1.0, Spring Cloud Alibaba 2025.1.0.0, Nacos 3.1.1, MyBatis-Plus 3.5.17, Springdoc 3.0.3, Docker Compose, Python unittest, PowerShell 7.

## Global Constraints

- Java is exactly 17 for source, target, Maven execution, IDEA, Docker build, and Docker runtime.
- Spring Boot is exactly 4.0.8; Spring Cloud is exactly 2025.1.0; Spring Cloud Alibaba is exactly 2025.1.0.0.
- MyBatis-Plus is exactly 3.5.17 and uses `mybatis-plus-spring-boot4-starter` plus `mybatis-plus-jsqlparser`.
- Springdoc is exactly 3.0.3; old Knife4j OpenAPI2/Springfox dependencies are removed.
- Nacos Server is exactly 3.1.1; existing Nacos data is preserved with non-destructive, repeatable schema changes.
- Preserve package names, artifact coordinates, controller routes, request/response shapes, service names, ports, databases, JWT behavior, Nginx ports, and the default Compose traffic topology.
- Do not globally replace `javax`; only Servlet and Validation imports become Jakarta. Keep `javax.crypto` and `javax.sql` unchanged.
- The worktree already contains user changes. Never use `git reset`, `git checkout`, or whole-file restoration. Stage only task-owned hunks; if a file contains inseparable pre-existing edits, leave it uncommitted and record the checkpoint.
- Use PowerShell 7 and UTF-8. Use `apply_patch` for project file edits.

---

## File Structure Map

| Responsibility | Files |
| --- | --- |
| Platform/BOM/module aggregation | `pom.xml`, `mall-backend/pom.xml`, every module `pom.xml` |
| Shared Boot auto-configuration | `mall-backend/hm-common/src/main/java/com/hmall/common/config/*.java`, `mall-backend/hm-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| Jakarta call sites | Servlet/Validation imports under `mall-backend`, `item-service`, `cart-service`, `user-service`, `trade-service`, `pay-service` |
| OpenAPI compatibility | new `mall-backend/hm-common/src/main/java/com/hmall/common/swagger/*`, service YAML, marketing `OpenApiConfig.java` |
| Nacos Config Data | five split-service `application.yaml` files; removal of their `bootstrap.yaml` files |
| Gateway | `hm-gateway/pom.xml`, `hm-gateway/src/main/resources/application.yaml`, gateway context/route tests |
| Nacos 3 runtime | `compose.yaml`, `.env.example`, `mysql/nacos.sql`, `mysql/nacos/custom.env` |
| Java 17 containers | six legacy Dockerfiles plus `marketing-service/Dockerfile` build-context adjustment |
| Regression contracts | `tests/test_java17_boot4_upgrade_contract.py`, `tests/test_nacos_shared_config_contract.py`, `tests/test_infrastructure_contract.py`, existing Java tests |
| Operator guidance | `README.md`, `scripts/Smoke-Test.ps1` only if its existing URLs require a verified compatibility update |

### Task 1: Protect the Dirty Worktree and Add the Upgrade Contract

**Files:**
- Create: `.codex-progress/boot4-migration/pre-change-status.txt`
- Create: `.codex-progress/boot4-migration/sha256.csv`
- Create: `.codex-progress/boot4-migration/files/` snapshots for every file modified by later tasks
- Create: `tests/test_java17_boot4_upgrade_contract.py`

**Interfaces:**
- Consumes: current repository state and approved design.
- Produces: a reproducible pre-change snapshot and executable final-version invariants used by all later tasks.

- [ ] **Step 1: Record the current status without altering the index**

Run:

```powershell
New-Item -ItemType Directory -Force .codex-progress\boot4-migration\files | Out-Null
git status --short | Set-Content -Encoding UTF8 .codex-progress\boot4-migration\pre-change-status.txt
```

Expected: the status file contains the existing modified, staged, and untracked paths, including the user's staged mapper rename.

- [ ] **Step 2: Snapshot only files listed in the File Structure Map**

Use `Copy-Item -Force` while preserving relative directories beneath `.codex-progress/boot4-migration/files`. Compute hashes with:

```powershell
Get-ChildItem .codex-progress\boot4-migration\files -Recurse -File |
  Get-FileHash -Algorithm SHA256 |
  Select-Object Path,Hash |
  Export-Csv -NoTypeInformation -Encoding UTF8 .codex-progress\boot4-migration\sha256.csv
```

Expected: no project file changes and a CSV containing one SHA-256 row per snapshot.

- [ ] **Step 3: Write the failing platform contract**

Create `tests/test_java17_boot4_upgrade_contract.py` with these checks:

```python
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
MODULES = (
    "mall-backend", "item-service", "hm-api", "cart-service",
    "user-service", "trade-service", "pay-service", "marketing-service",
    "hm-gateway",
)


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


class Java17Boot4UpgradeContractTest(unittest.TestCase):
    def test_root_owns_the_platform_and_all_modules(self):
        root = ET.parse(ROOT / "pom.xml").getroot()
        parent = root.find("m:parent", NS)
        self.assertEqual("org.springframework.boot", parent.findtext("m:groupId", namespaces=NS))
        self.assertEqual("spring-boot-starter-parent", parent.findtext("m:artifactId", namespaces=NS))
        self.assertEqual("4.0.8", parent.findtext("m:version", namespaces=NS))
        self.assertEqual("17", root.findtext("m:properties/m:java.version", namespaces=NS))
        modules = [node.text.strip() for node in root.findall("m:modules/m:module", NS)]
        self.assertEqual(set(MODULES), set(modules))

    def test_platform_versions_are_single_sourced(self):
        root = read("pom.xml")
        self.assertIn("<spring-cloud.version>2025.1.0</spring-cloud.version>", root)
        self.assertIn("<spring-cloud-alibaba.version>2025.1.0.0</spring-cloud-alibaba.version>", root)
        self.assertIn("<mybatis-plus.version>3.5.17</mybatis-plus.version>", root)
        self.assertIn("<springdoc.version>3.0.3</springdoc.version>", root)
        for pom in ROOT.rglob("pom.xml"):
            if pom == ROOT / "pom.xml":
                continue
            source = pom.read_text(encoding="utf-8")
            self.assertNotRegex(source, r"<java.version>11</java.version>", str(pom))
            self.assertNotIn("mybatis-plus-boot-starter", source, str(pom))
            self.assertNotIn("knife4j-openapi2-spring-boot-starter", source, str(pom))
            self.assertNotIn("spring-cloud-starter-bootstrap", source, str(pom))

    def test_java_ee_imports_use_jakarta(self):
        offenders = []
        for java in ROOT.rglob("*.java"):
            source = java.read_text(encoding="utf-8")
            if re.search(r"import javax\.(servlet|validation)\.", source):
                offenders.append(str(java.relative_to(ROOT)))
        self.assertEqual([], offenders)

    def test_all_java_dockerfiles_use_temurin_17(self):
        dockerfiles = [
            "mall-backend/hm-service/Dockerfile", "item-service/Dockerfile",
            "cart-service/Dockerfile", "user-service/Dockerfile",
            "trade-service/Dockerfile", "pay-service/Dockerfile",
            "marketing-service/Dockerfile",
        ]
        for path in dockerfiles:
            with self.subTest(path=path):
                source = read(path)
                self.assertIn("eclipse-temurin-17", source)
                self.assertIn("eclipse-temurin:17-jre", source)
                self.assertNotIn("temurin-11", source)

    def test_boot4_auto_configuration_registration_exists(self):
        imports = "mall-backend/hm-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        self.assertTrue((ROOT / imports).is_file())
        source = read(imports)
        for name in ("MyBatisConfig", "MvcConfig", "JsonConfig", "OpenApiAutoConfiguration"):
            self.assertIn(name, source)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 4: Run the new test and verify the baseline failure**

Run:

```powershell
python -m unittest tests.test_java17_boot4_upgrade_contract -v
```

Expected: FAIL on the missing Boot 4 root parent, Java 11 POM values, old imports, old Docker images, and missing `AutoConfiguration.imports`.

- [ ] **Step 5: Commit only the new contract test**

Run:

```powershell
git add -- tests/test_java17_boot4_upgrade_contract.py
git diff --cached --check -- tests/test_java17_boot4_upgrade_contract.py
git commit --only -m "test: define JDK 17 Boot 4 migration contract" -- tests/test_java17_boot4_upgrade_contract.py
```

Expected: only the new contract test is committed; existing staged user changes remain staged.

### Task 2: Centralize the Maven Platform on the Root POM

**Files:**
- Modify: `pom.xml`
- Modify: `mall-backend/pom.xml`
- Modify: `hm-api/pom.xml`
- Modify: `hm-gateway/pom.xml`
- Modify: `item-service/pom.xml`
- Modify: `cart-service/pom.xml`
- Modify: `user-service/pom.xml`
- Modify: `trade-service/pom.xml`
- Modify: `pay-service/pom.xml`
- Modify: `marketing-service/pom.xml`
- Modify: `mall-backend/hm-common/pom.xml`
- Modify: `mall-backend/hm-service/pom.xml`

**Interfaces:**
- Consumes: Task 1 platform contract.
- Produces: one Boot/Cloud/Alibaba dependency-management tree and Java 17 compiler settings for every module.

- [ ] **Step 1: Make the root POM the Boot 4 parent**

Add the Boot parent, properties, and BOM imports to `pom.xml`:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.8</version>
    <relativePath/>
</parent>

<properties>
    <java.version>17</java.version>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    <spring-cloud.version>2025.1.0</spring-cloud.version>
    <spring-cloud-alibaba.version>2025.1.0.0</spring-cloud-alibaba.version>
    <mybatis-plus.version>3.5.17</mybatis-plus.version>
    <springdoc.version>3.0.3</springdoc.version>
</properties>
```

Import `spring-cloud-dependencies` followed by `spring-cloud-alibaba-dependencies`, both with `type=pom` and `scope=import`. In the same `<dependencyManagement>` block manage `mybatis-plus-spring-boot4-starter` and `mybatis-plus-jsqlparser` with `${mybatis-plus.version}`, and `springdoc-openapi-starter-webmvc-ui` with `${springdoc.version}`. Add `hm-gateway` after `hm-api` in `<modules>` so shared jars build before the gateway.

- [ ] **Step 2: Make `mall-backend` inherit the root without changing its coordinates**

Use this parent and keep `com.heima:hmall:1.0.0`:

```xml
<parent>
    <groupId>com.dongqh</groupId>
    <artifactId>hmall-lottery</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>
<groupId>com.heima</groupId>
<artifactId>hmall</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>
```

Remove its Boot 2 parent, Java 11 compiler properties, Cloud 2021 BOMs, MyBatis 3.4.3 management, old MySQL version, explicit Lombok version, and compiler plugin source/target 11. Keep Hutool management and inherited test dependencies that are still used.

- [ ] **Step 3: Remove module-local Java 11 settings**

Delete only `<java.version>11</java.version>`, `<maven.compiler.source>11</maven.compiler.source>`, and `<maven.compiler.target>11</maven.compiler.target>` from all child POMs. Preserve every artifact coordinate and business dependency.

Add `<relativePath>../mall-backend/pom.xml</relativePath>` to the existing `com.heima:hmall:1.0.0` parent in `hm-gateway/pom.xml`; without this explicit path Maven defaults to the unrelated root POM.

- [ ] **Step 4: Make marketing inherit the root**

Replace the Boot parent in `marketing-service/pom.xml` with:

```xml
<parent>
    <groupId>com.dongqh</groupId>
    <artifactId>hmall-lottery</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

Remove its duplicate Java, MyBatis-Plus, and Springdoc version properties while retaining JJWT, OSS, and Redisson versions.

- [ ] **Step 5: Validate the Maven model before source migration**

Run:

```powershell
mvn -B -ntp -N help:effective-pom -Doutput=.codex-progress\boot4-migration\root-effective-pom.xml
mvn -B -ntp validate -DskipTests
```

Expected: Maven resolves the root parent and both BOMs. `validate` succeeds; Java compilation is not expected in this step.

- [ ] **Step 6: Run only the platform assertions**

Run:

```powershell
python -m unittest tests.test_java17_boot4_upgrade_contract.Java17Boot4UpgradeContractTest.test_root_owns_the_platform_and_all_modules tests.test_java17_boot4_upgrade_contract.Java17Boot4UpgradeContractTest.test_platform_versions_are_single_sourced -v
```

Expected: the root assertion passes; the single-source assertion remains red until old dependency artifact IDs are removed in Task 3.

- [ ] **Step 7: Create a Maven checkpoint without capturing unrelated changes**

Inspect each path with `git diff -- <path>`. Commit only clean task-owned POM changes. If an untracked POM already contains user work, leave it uncommitted and append its path to `.codex-progress/boot4-migration/pre-existing-overlap.txt`.

### Task 3: Replace Boot 2 Dependencies and Migrate Known Boot 4 APIs

**Files:**
- Modify: all POMs listed in Task 2
- Modify: all Java files containing `javax.servlet` or `javax.validation`
- Modify: six `Http*Client.java` files importing the old `RestTemplateBuilder`
- Modify: `cart-service/src/test/java/com/dongqh/cartservice/CartServiceApplicationTests.java`
- Modify: `item-service/src/test/java/com/dongqh/itemservice/ItemServiceApplicationTests.java`
- Modify: `mall-backend/hm-common/src/main/java/com/hmall/common/config/JsonConfig.java`
- Modify: `mall-backend/hm-common/src/main/java/com/hmall/common/config/MvcConfig.java`
- Create: `mall-backend/hm-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `mall-backend/hm-common/src/test/java/com/hmall/common/config/JsonConfigTest.java`

**Interfaces:**
- Consumes: the root Boot 4 platform from Task 2.
- Produces: Boot 4-compilable shared code with the same validation, Servlet interception, HTTP client, and long-number JSON behavior.

- [ ] **Step 1: Write the JSON behavior test**

Create `JsonConfigTest.java`:

```java
package com.hmall.common.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonConfigTest {
    @Test
    void serializesLongAndBigIntegerAsStrings() throws Exception {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JsonConfig().jsonMapperBuilderCustomizer().customize(builder);
        JsonMapper mapper = builder.build();
        assertEquals("\"9007199254740993\"", mapper.writeValueAsString(9007199254740993L));
        assertEquals("\"9007199254740995\"", mapper.writeValueAsString(new BigInteger("9007199254740995")));
    }
}
```

- [ ] **Step 2: Replace dependency artifact IDs exactly**

Apply these replacements:

| Old | New |
| --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `mybatis-plus-boot-starter` | `mybatis-plus-spring-boot4-starter` |
| `mysql:mysql-connector-java` | `com.mysql:mysql-connector-j` with runtime scope |
| `spring-cloud-starter-gateway` | leave unchanged in this task; Task 6 replaces it with `spring-cloud-starter-gateway-server-webflux` and verifies the route model |
| `spring-cloud-starter-bootstrap` | remove |

Every MyBatis runnable service also receives `com.baomidou:mybatis-plus-jsqlparser` without a local version. Services that construct `RestTemplate` receive `org.springframework.boot:spring-boot-starter-restclient`.

Mark `spring-webmvc`, Tomcat, MyBatis, Springdoc, and other Servlet-only dependencies in `hm-common` as optional where they are needed only to compile shared optional auto-configuration. This prevents them from leaking into Gateway.

- [ ] **Step 3: Migrate only Servlet and Validation imports**

For every match returned by:

```powershell
rg -l --glob '*.java' 'import javax\.(servlet|validation)\.' mall-backend item-service cart-service user-service trade-service pay-service
```

replace `javax.servlet` with `jakarta.servlet` and `javax.validation` with `jakarta.validation`. Run the same `rg` command afterward; expected output is empty.

- [ ] **Step 4: Migrate known Boot package moves**

In these six clients:

- `mall-backend/hm-service/.../HttpCouponCheckoutClient.java`
- `mall-backend/hm-service/.../HttpMembershipBenefitClient.java`
- `trade-service/.../HttpCouponCheckoutClient.java`
- `trade-service/.../HttpMembershipBenefitClient.java`
- `pay-service/.../HttpCouponCheckoutClient.java`
- `pay-service/.../HttpMembershipBenefitClient.java`

replace:

```java
import org.springframework.boot.web.client.RestTemplateBuilder;
```

with:

```java
import org.springframework.boot.restclient.RestTemplateBuilder;
```

In the cart test replace Boot's removed `@MockBean` with:

```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@MockitoBean
private ItemClient itemClient;
```

In the item test use:

```java
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
```

- [ ] **Step 5: Replace the Jackson 2 customizer with Jackson 3**

Implement `JsonConfig` with `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`, `tools.jackson.databind.module.SimpleModule`, and `tools.jackson.databind.ser.std.ToStringSerializer`. Register serializers for `Long.class`, `Long.TYPE`, and `BigInteger.class` so primitive and boxed long responses remain strings.

- [ ] **Step 6: Make MVC auto-configuration Servlet-only**

Add this class-level condition to `MvcConfig`:

```java
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
```

Keep `@ConditionalOnClass(DispatcherServlet.class)` and the existing interceptor registration unchanged.

- [ ] **Step 7: Register Boot 4 auto-configurations**

Create `AutoConfiguration.imports` containing exactly:

```text
com.hmall.common.config.MyBatisConfig
com.hmall.common.config.MvcConfig
com.hmall.common.config.JsonConfig
com.hmall.common.swagger.OpenApiAutoConfiguration
```

Keep `spring.factories` until the new mechanism is verified, then remove only its `EnableAutoConfiguration` entry so duplicate configuration cannot occur.

- [ ] **Step 8: Run shared and compilation tests**

Run:

```powershell
mvn -B -ntp -pl mall-backend/hm-common -am test
mvn -B -ntp -pl hm-api,item-service,cart-service,user-service,trade-service,pay-service,mall-backend/hm-service -am test-compile -DskipTests
python -m unittest tests.test_java17_boot4_upgrade_contract.Java17Boot4UpgradeContractTest.test_java_ee_imports_use_jakarta -v
```

Expected: shared JSON/interceptor tests pass; all listed modules compile their main and test sources; no prohibited `javax` import remains.

### Task 4: Preserve API Documentation with Springdoc 3

**Files:**
- Create: `mall-backend/hm-common/src/main/java/com/hmall/common/swagger/SwaggerProperties.java`
- Create: `mall-backend/hm-common/src/main/java/com/hmall/common/swagger/OpenApiAutoConfiguration.java`
- Modify: Servlet service POMs and `mall-backend/hm-common/pom.xml`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/config/OpenApiConfig.java`
- Modify: `marketing-service/src/test/java/com/dongqh/luckyhub/config/OpenApiConfigTests.java`
- Modify: `item-service/src/test/java/com/dongqh/itemservice/ItemServiceApplicationTests.java`

**Interfaces:**
- Consumes: existing `hm.swagger.title`, `description`, and `package` YAML properties.
- Produces: `/v3/api-docs`, Swagger UI, and backward-compatible `/doc.html` entry without changing business endpoints.

- [ ] **Step 1: Write failing `/doc.html` compatibility assertions**

In the item and marketing MockMvc tests add:

```java
mockMvc.perform(get("/doc.html"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/swagger-ui.html"));
```

Expected before implementation: FAIL with 404.

- [ ] **Step 2: Replace Knife4j dependencies**

Remove every `knife4j-openapi2-spring-boot-starter`. Add `org.springdoc:springdoc-openapi-starter-webmvc-ui` without a local version to item, cart, user, trade, pay, and hm-service. Keep the same dependency optional in hm-common only for compiling its auto-configuration.

- [ ] **Step 3: Bind the existing `hm.swagger` keys**

Implement `SwaggerProperties` with fields `title`, `description`, and internal `controllerPackage`; expose JavaBean methods `getPackage()` and `setPackage(String)` so the existing YAML key named `package` remains unchanged.

- [ ] **Step 4: Produce OpenAPI beans and the compatibility redirect**

Implement `OpenApiAutoConfiguration` as `@AutoConfiguration`, conditional on a Servlet web application and `GroupedOpenApi`. Enable `SwaggerProperties`, create an `OpenAPI` bean with `Info.title/description`, create a `GroupedOpenApi` named `default` scanning `properties.getPackage()`, and register `/doc.html` → `/swagger-ui.html` using a `WebMvcConfigurer` view controller.

- [ ] **Step 5: Add the same redirect to marketing's existing OpenAPI configuration**

Keep marketing's existing OpenAPI metadata and security scheme. Add only a `WebMvcConfigurer` bean that registers the `/doc.html` redirect.

- [ ] **Step 6: Run API documentation tests**

Run:

```powershell
mvn -B -ntp -pl item-service,marketing-service -am -Dtest=ItemServiceApplicationTests,OpenApiConfigTests test
```

Expected: `/doc.html` redirects, `/v3/api-docs` returns 200, and existing title/security assertions pass.

### Task 5: Migrate Five Services from Bootstrap to Config Data

**Files:**
- Modify: `tests/test_nacos_shared_config_contract.py`
- Modify: five `src/main/resources/application.yaml` files
- Delete: five `src/main/resources/bootstrap.yaml` files

**Interfaces:**
- Consumes: existing service name, profile, Nacos credentials, three shared Data IDs, and local/dev profile YAML.
- Produces: the same configuration sources through Spring Cloud Alibaba 2025.1 `spring.config.import`.

- [ ] **Step 1: Rewrite the Nacos contract to require Config Data**

Change the first two tests so each service must have Nacos config/discovery dependencies, must not have Bootstrap starter/file, and its main application YAML must contain exactly these imports:

```text
optional:nacos:shared.jdbc.yaml?group=DEFAULT_GROUP&refreshEnabled=true
optional:nacos:shared.logger.yaml?group=DEFAULT_GROUP&refreshEnabled=true
optional:nacos:shared.swagger.yaml?group=DEFAULT_GROUP&refreshEnabled=true
optional:nacos:${spring.application.name}.yaml?group=DEFAULT_GROUP&refreshEnabled=true
```

Keep assertions for environment variables, service names, `DEFAULT_GROUP`, controller packages, titles, and database isolation.

- [ ] **Step 2: Verify the rewritten contract fails**

Run:

```powershell
python -m unittest tests.test_nacos_shared_config_contract -v
```

Expected: FAIL because Bootstrap files and starter still exist and Config Data imports are absent.

- [ ] **Step 3: Merge Bootstrap content into each application YAML**

For each service retain its current `server.port`, discovery flags, transport settings, and `hm.swagger` block. Add `spring.application.name`, `spring.profiles.active`, Nacos server/credentials/config group, and the four imports from Step 1. Use the existing service name exactly: `item-service`, `cart-service`, `user-service`, `trade-service`, or `pay-service`.

- [ ] **Step 4: Delete Bootstrap resources using patch deletion**

Delete only the five `bootstrap.yaml` files after their values appear in the corresponding `application.yaml`. Confirm with:

```powershell
rg -n 'shared-configs|spring-cloud-starter-bootstrap' item-service cart-service user-service trade-service pay-service
```

Expected: no output.

- [ ] **Step 5: Run Config Data contracts**

Run:

```powershell
python -m unittest tests.test_nacos_shared_config_contract tests.test_java17_boot4_upgrade_contract -v
```

Expected: Nacos config tests pass; Docker assertions may remain red until Task 7.

### Task 6: Upgrade and Isolate the Reactive Gateway

**Files:**
- Modify: `hm-gateway/pom.xml`
- Modify: `hm-gateway/src/main/resources/application.yaml`
- Modify: `hm-gateway/src/test/java/com/dongqh/hmgateway/HmGatewayApplicationTests.java`

**Interfaces:**
- Consumes: `hm-common` exceptions, Nacos discovery, five fixed route definitions, JWT and anonymous path configuration.
- Produces: a Boot 4 reactive Gateway on port 8080 with unchanged routing and authentication semantics.

- [ ] **Step 1: Write the route contract in the Gateway context test**

Disable Nacos discovery in test properties, autowire `RouteDefinitionLocator`, collect routes, and assert this exact map:

```java
Map.of(
    "item-service", "lb://item-service",
    "cart-service", "lb://cart-service",
    "user-service", "lb://user-service",
    "trade-service", "lb://trade-service",
    "pay-service", "lb://pay-service"
)
```

Keep a separate assertion that `AuthProperties.excludePaths` contains `/search/**`, `/users/login`, `/items/**`, and `/hi`.

- [ ] **Step 2: Replace the Gateway starter**

Use:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>
```

Keep Nacos discovery and load-balancer dependencies. Do not add WebMvc or Tomcat.

- [ ] **Step 3: Move Gateway routes to the Boot 4 property namespace**

Nest the existing route list beneath:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
```

Copy every existing id, URI, predicate, and filter unchanged.

- [ ] **Step 4: Verify the reactive dependency boundary**

Run:

```powershell
mvn -B -ntp -pl hm-gateway -am dependency:tree -Dincludes=org.springframework:spring-webmvc,org.apache.tomcat.embed:tomcat-embed-core
mvn -B -ntp -pl hm-gateway -am test
```

Expected: dependency filter prints no Servlet MVC/Tomcat dependency for `hm-gateway`; route and context tests pass.

### Task 7: Upgrade Nacos Runtime and All Java Docker Images

**Files:**
- Modify: `tests/test_infrastructure_contract.py`
- Modify: `compose.yaml`
- Modify: `.env.example`
- Modify: `mysql/nacos.sql`
- Modify: `mysql/nacos/custom.env`
- Modify: seven Java-service Dockerfiles

**Interfaces:**
- Consumes: existing persistent MySQL volume, Nacos credentials, host client port 18848, gRPC port 19848, and service Docker build layout.
- Produces: Nacos 3.1.1 with a separate console port and Java 17 application images.

- [ ] **Step 1: Update infrastructure contracts first**

Change expected Nacos image to `nacos/nacos-server:v3.1.1`. Require port targets 8080, 8848, and 9848; require host default console port 18048; require readiness path `/v3/console/health/readiness`; require all Java Dockerfiles to use version 17. Add schema assertions for `config_info_gray`, `publish_type`, `gray_name`, and `ext_info`, while retaining the no-drop/no-delete assertions.

- [ ] **Step 2: Verify infrastructure tests fail**

Run:

```powershell
python -m unittest tests.test_infrastructure_contract -v
```

Expected: FAIL on the Nacos image/console/schema and six Java 11 Dockerfiles.

- [ ] **Step 3: Make the Nacos 3 schema repeatable and non-destructive**

In `mysql/nacos.sql`:

- change the schema comment to Nacos 3.1.1;
- add `CREATE TABLE IF NOT EXISTS config_info_gray` using the official 3.1.1 columns and indexes;
- add `his_config_info.publish_type varchar(50) DEFAULT 'formal'`, `gray_name varchar(50) DEFAULT NULL`, and `ext_info longtext DEFAULT NULL` through `information_schema.columns` guarded dynamic `ALTER TABLE` statements;
- keep old tables in place because deleting them is unnecessary and destructive;
- keep the dedicated `nacos_app` user and idempotent `nacos` role/user seed.

- [ ] **Step 4: Upgrade the Compose Nacos service**

Use `nacos/nacos-server:v3.1.1`, retain `18848:8848` and `19848:9848`, and add:

```yaml
- "${NACOS_CONSOLE_PORT:-18048}:8080"
```

Set `NACOS_CONSOLE_PORT: "8080"` in the container environment and change the health check to:

```text
curl -fsS http://127.0.0.1:8080/v3/console/health/readiness
```

Add `NACOS_CONSOLE_PORT=18048` to `.env.example`. Preserve application client addresses and credentials.

- [ ] **Step 5: Make root-parent modules buildable from Docker**

Change `mall-service` and `marketing-service` build contexts in `compose.yaml` to repository root and keep their Dockerfile paths explicit. Update both Dockerfiles to `COPY . .` and build with:

```text
mvn -B -ntp -pl mall-backend/hm-service -am package -Dmaven.test.skip=true
mvn -B -ntp -pl marketing-service -am package -Dmaven.test.skip=true
```

This makes the root parent POM available inside both builds.

- [ ] **Step 6: Upgrade every application image to Java 17**

Replace each build image with `maven:3.9.9-eclipse-temurin-17` and each runtime image with `eclipse-temurin:17-jre`. Do not change exposed application ports or entrypoint jar paths.

- [ ] **Step 7: Validate Compose and infrastructure contracts**

Run:

```powershell
docker compose --env-file .env.example config --format json | Out-Null
python -m unittest tests.test_infrastructure_contract tests.test_java17_boot4_upgrade_contract -v
```

Expected: Compose parses and both contract suites pass.

### Task 8: Align Tests, README, and Local IDEA Guidance

**Files:**
- Modify: Java tests still using Boot 2 test packages
- Modify: `README.md`
- Inspect and minimally modify if present: `.idea/misc.xml`, `.idea/compiler.xml`, `.idea/workspace.xml`

**Interfaces:**
- Consumes: Boot 4 modular test packages and the final local runtime ports.
- Produces: reproducible JDK 17 commands and no obsolete `--add-opens` workaround.

- [ ] **Step 1: Scan for removed test APIs and Java 11 instructions**

Run:

```powershell
rg -n 'org\.springframework\.boot\.test\.mock\.mockito|org\.springframework\.boot\.test\.autoconfigure\.web|@MockBean|@SpyBean|JDK 11|Java 11|temurin-11|--add-opens=java\.base/java\.lang\.invoke' . --glob '!docs/superpowers/**' --glob '!.codex-progress/**'
```

Expected: only real remaining migration targets are printed; historical design documents and snapshots are excluded.

- [ ] **Step 2: Migrate remaining tests explicitly**

Use `org.springframework.test.context.bean.override.mockito.MockitoBean`/`MockitoSpyBean`, `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, and the technology-specific Boot 4 test starter required by each test. Do not weaken assertions or disable tests.

- [ ] **Step 3: Update local instructions**

Document:

- `java -version` must report 17;
- IDEA Project SDK and Maven Runner use JDK 17;
- local Nacos client remains `127.0.0.1:18848`;
- Nacos 3 console is `http://localhost:18048/`;
- Gateway remains `http://localhost:8080`;
- local Nginx portal remains `http://localhost:18080`;
- startup order is infrastructure → five services → marketing → gateway → local Nginx.

- [ ] **Step 4: Remove only the obsolete VM workaround**

If `.idea/workspace.xml` contains `--add-opens=java.base/java.lang.invoke=ALL-UNNAMED`, remove that exact token without changing other run-configuration options. Verify `.idea/misc.xml` and `.idea/compiler.xml` still select 17; do not rewrite IDE files that are already correct.

- [ ] **Step 5: Re-run the residue scan**

Expected: no active POM, Dockerfile, Java source, README, or IDEA run configuration contains the old Java 11/Boot 2 workaround values.

### Task 9: Full Build and Behavior Verification

**Files:**
- Modify only when a failing assertion proves a behavior-preserving compatibility defect in a task-owned migration hunk.
- Record: `.codex-progress/boot4-migration/verification.txt`

**Interfaces:**
- Consumes: all previous task deliverables.
- Produces: evidence that the final repository builds and preserves externally observable behavior.

- [ ] **Step 1: Verify tools and effective versions**

Run:

```powershell
java -version
mvn -version
mvn -B -ntp help:effective-pom -Doutput=.codex-progress\boot4-migration\effective-pom.xml
```

Expected: Java and Maven runtime report JDK 17; the effective POM contains Boot 4.0.8 and no Boot 2.7.12.

- [ ] **Step 2: Run all repository contract tests**

Run:

```powershell
python -m unittest discover -s tests -p 'test_*.py' -v
node tests\test_checkout_frontend.cjs
```

Expected: all Python and Node contract tests pass without skipped assertions introduced by this migration.

- [ ] **Step 3: Compile the complete Maven reactor**

Run:

```powershell
mvn -B -ntp clean test-compile -DskipTests
```

Expected: every module, including `hm-gateway`, compiles with release 17.

- [ ] **Step 4: Run Java tests by risk boundary**

Run:

```powershell
mvn -B -ntp -pl mall-backend/hm-common,hm-api,item-service,cart-service,user-service,trade-service,pay-service,hm-gateway,mall-backend/hm-service -am test
mvn -B -ntp -pl marketing-service -am test
```

Expected: all tests pass. Do not convert integration failures into ignored tests; report unavailable external dependencies explicitly.

- [ ] **Step 5: Build application packages and Docker images**

Run:

```powershell
mvn -B -ntp package -DskipTests
docker compose --env-file .env.example build item-service cart-service user-service trade-service pay-service marketing-service
docker compose --env-file .env.example --profile rollback build mall-service
```

Expected: all jars and Java 17 images build successfully.

- [ ] **Step 6: Start infrastructure and verify Nacos before applications**

Run with the user's real `.env`:

```powershell
docker compose --env-file .env up -d mysql redis schema-init nacos
docker compose --env-file .env ps
Invoke-RestMethod http://localhost:18048/v3/console/health/readiness
```

Expected: MySQL and Redis are healthy, schema-init exits 0, Nacos is healthy, and existing `config_info` rows remain present.

- [ ] **Step 7: Start services and run HTTP smoke tests**

Start the five services and marketing using the existing documented workflow, then run:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Smoke-Test.ps1
```

Additionally start `HmGatewayApplication` on JDK 17 and verify `/users/login`, `/search/list`, `/items/{id}`, `/carts`, `/addresses`, `/orders/me`, and `/pay-orders` through `http://localhost:8080`.

Expected: `Jack / 123` logs in, authorization propagation works, and core responses retain their prior shapes.

- [ ] **Step 8: Verify local Nginx frontend integration**

Run local Nginx and open `http://localhost:18080/login.html`. Verify login, search, item detail, cart, address selection, checkout, order display, payment, and marketing pages against local Java services.

Expected: browser network requests return the same success/error semantics as before the migration; no request is accidentally sent to the packaged Docker Java application when local services are the intended target.

- [ ] **Step 9: Save verification evidence**

Append the exact commands, exit codes, service health, and any environment-limited checks to `.codex-progress/boot4-migration/verification.txt`. Do not claim an unchecked runtime path passed.

### Task 10: Final Residue Audit and Safe Handoff

**Files:**
- Modify: no product files unless an audit assertion finds a task-owned residue.
- Update: `docs/superpowers/plans/2026-09-11-jdk17-spring-boot4-migration.md` checkboxes during execution.

**Interfaces:**
- Consumes: verification evidence and the pre-change manifest.
- Produces: a reviewable final diff with no old platform residue and no lost user edits.

- [ ] **Step 1: Run final residue searches**

Run:

```powershell
rg -n '2\.7\.12|2021\.0\.3|2021\.0\.4\.0|3\.4\.3|temurin-11|<java.version>11|<maven.compiler.(source|target)>11|mybatis-plus-boot-starter|knife4j-openapi2-spring-boot-starter|spring-cloud-starter-bootstrap|import javax\.(servlet|validation)' . --glob '!docs/superpowers/**' --glob '!.codex-progress/**'
```

Expected: no output from active project files.

- [ ] **Step 2: Compare worktree ownership**

Compare `git status --short` with `.codex-progress/boot4-migration/pre-change-status.txt`. Confirm every pre-existing path still exists with its content preserved except for explicitly approved upgrade hunks.

- [ ] **Step 3: Inspect the final diff**

Run:

```powershell
git diff --check
git diff --stat
git status --short
```

Expected: no whitespace errors, no secrets, no build outputs, and no unrelated deletions.

- [ ] **Step 4: Commit only separable migration changes**

Stage explicit paths or task-owned hunks. Before each commit run `git diff --cached --check` and `git diff --cached`. Leave inseparable pre-existing user changes uncommitted and list them in the handoff.

- [ ] **Step 5: Report completion with evidence**

Report target versions, modified areas, exact tests/builds run, runtime smoke results, unverified checks, user changes deliberately left untouched, and how to start the JDK 17 services for local Nginx integration.
