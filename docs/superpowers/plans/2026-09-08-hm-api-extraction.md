# HM API Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `hm-api` into a Java 11 shared OpenFeign contract library and make `cart-service` consume its `ItemClient` and `ItemDTO`.

**Architecture:** `hm-api` is a non-runnable JAR inheriting the existing HMall parent and containing only remote API contracts. `cart-service` owns the Nacos, OpenFeign and LoadBalancer runtime and explicitly scans the client package exported by `hm-api`; `item-service` remains an HTTP provider and does not depend on the client library.

**Tech Stack:** Java 11 bytecode, Spring Boot 2.7.12, Spring Cloud 2021.0.3, Spring Cloud Alibaba 2021.0.4.0, OpenFeign 3.1.x, Maven 3.9.2, JUnit/Python contract tests

## Global Constraints

- `hm-api` must inherit `com.heima:hmall:1.0.0` from `../mall-backend/pom.xml`.
- `hm-api` must use Java 11 and coordinates `com.heima:hm-api:1.0.0`.
- `hm-api` is not a Spring Boot application and must not contain runtime configuration.
- `hm-api` must not depend on `cart-service`, Nacos, LoadBalancer, Knife4j, Web or database libraries.
- `cart-service` retains its current Java 11 / Spring Boot 2.7.12 runtime dependencies.
- Existing unrelated working-tree changes must remain untouched.

---

### Task 1: Lock the Maven and module boundary in contract tests

**Files:**
- Create: `tests/test_hm_api_contract.py`

**Interfaces:**
- Consumes: root `pom.xml`, `hm-api/pom.xml`, and Java source paths.
- Produces: executable assertions describing the final module boundary.

- [ ] **Step 1: Write the failing contract tests**

```python
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def pom(path: str) -> ET.Element:
    return ET.parse(ROOT / path).getroot()


def text(node: ET.Element, path: str) -> str | None:
    found = node.find(path, NS)
    return found.text.strip() if found is not None and found.text else None


class HmApiContractTest(unittest.TestCase):
    def test_reactor_contains_hm_api_before_cart(self):
        modules = [n.text.strip() for n in pom("pom.xml").findall("m:modules/m:module", NS)]
        self.assertIn("hm-api", modules)
        self.assertLess(modules.index("hm-api"), modules.index("cart-service"))

    def test_hm_api_matches_hmall_java_11_baseline(self):
        api = pom("hm-api/pom.xml")
        parent = api.find("m:parent", NS)
        self.assertEqual("com.heima", text(parent, "m:groupId"))
        self.assertEqual("hmall", text(parent, "m:artifactId"))
        self.assertEqual("1.0.0", text(parent, "m:version"))
        self.assertEqual("../mall-backend/pom.xml", text(parent, "m:relativePath"))
        self.assertEqual("hm-api", text(api, "m:artifactId"))
        self.assertEqual("11", text(api, "m:properties/m:java.version"))

    def test_hm_api_is_a_non_runnable_openfeign_contract_jar(self):
        api = pom("hm-api/pom.xml")
        dependencies = {
            (text(n, "m:groupId"), text(n, "m:artifactId"))
            for n in api.findall("m:dependencies/m:dependency", NS)
        }
        self.assertEqual({("org.springframework.cloud", "spring-cloud-starter-openfeign")}, dependencies)
        self.assertIsNone(api.find("m:build/m:plugins/m:plugin", NS))
        self.assertFalse((ROOT / "hm-api/src/main/java/com/dongqh/hmapi/HmApiApplication.java").exists())
        self.assertFalse((ROOT / "hm-api/src/main/resources/application.yaml").exists())

    def test_contract_does_not_reference_cart_service(self):
        client = (ROOT / "hm-api/src/main/java/com/dongqh/hmapi/client/ItemClient.java").read_text("utf-8")
        self.assertIn("com.dongqh.hmapi.dto.ItemDTO", client)
        self.assertNotIn("cartservice", client)

    def test_cart_consumes_hm_api_without_duplicate_contracts(self):
        cart = pom("cart-service/pom.xml")
        dependencies = {
            (text(n, "m:groupId"), text(n, "m:artifactId"))
            for n in cart.findall("m:dependencies/m:dependency", NS)
        }
        self.assertIn(("com.heima", "hm-api"), dependencies)
        self.assertFalse((ROOT / "cart-service/src/main/java/com/dongqh/cartservice/client/ItemClient.java").exists())
        self.assertFalse((ROOT / "cart-service/src/main/java/com/dongqh/cartservice/domain/dto/ItemDTO.java").exists())
        app = (ROOT / "cart-service/src/main/java/com/dongqh/cartservice/CartServiceApplication.java").read_text("utf-8")
        self.assertIn('basePackages = "com.dongqh.hmapi.client"', app)
```

- [ ] **Step 2: Run the contract test and verify RED**

Run:

```powershell
python -m unittest tests.test_hm_api_contract
```

Expected: FAIL because `hm-api` is missing from the reactor, still uses Boot 4.1.1/Java 17, references `cart-service`, and is still runnable.

- [ ] **Step 3: Commit the failing contract**

```powershell
git add tests/test_hm_api_contract.py
git commit -m "test: define hm-api module contract"
```

### Task 2: Convert hm-api into a Java 11 contract library

**Files:**
- Modify: `pom.xml`
- Modify: `hm-api/pom.xml`
- Modify: `hm-api/src/main/java/com/dongqh/hmapi/client/ItemClient.java`
- Modify: `hm-api/src/main/java/com/dongqh/hmapi/dto/ItemDTO.java`
- Delete: `hm-api/src/main/java/com/dongqh/hmapi/HmApiApplication.java`
- Delete: `hm-api/src/main/resources/application.yaml`
- Delete: `hm-api/src/test/java/com/dongqh/hmapi/HmApiApplicationTests.java`

**Interfaces:**
- Consumes: dependency management from `mall-backend/pom.xml`.
- Produces: `com.heima:hm-api:1.0.0`, `ItemClient#queryItemByIds(Collection<Long>)`, and `com.dongqh.hmapi.dto.ItemDTO`.

- [ ] **Step 1: Add hm-api to the root reactor before cart-service**

```xml
<module>item-service</module>
<module>hm-api</module>
<module>cart-service</module>
```

- [ ] **Step 2: Replace hm-api POM with the shared Java 11 baseline**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.heima</groupId>
        <artifactId>hmall</artifactId>
        <version>1.0.0</version>
        <relativePath>../mall-backend/pom.xml</relativePath>
    </parent>
    <artifactId>hm-api</artifactId>
    <name>hm-api</name>
    <description>HMall shared OpenFeign API contracts</description>
    <properties>
        <java.version>11</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Fix ItemClient to use the public DTO**

```java
package com.dongqh.hmapi.client;

import com.dongqh.hmapi.dto.ItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient("item-service")
public interface ItemClient {
    @GetMapping("/items")
    List<ItemDTO> queryItemByIds(@RequestParam("ids") Collection<Long> ids);
}
```

- [ ] **Step 4: Remove Swagger annotations from ItemDTO**

```java
package com.dongqh.hmapi.dto;

import lombok.Data;

@Data
public class ItemDTO {
    private Long id;
    private String name;
    private Integer price;
    private Integer stock;
    private String image;
    private String category;
    private String brand;
    private String spec;
    private Integer sold;
    private Integer commentCount;
    private Boolean isAD;
    private Integer status;
}
```

Removing the Swagger imports and annotations keeps the contract module independent of Knife4j.

- [ ] **Step 5: Delete runtime-only application files**

Delete the application class, `application.yaml`, and `HmApiApplicationTests`; a library JAR has no server lifecycle or Spring context of its own.

- [ ] **Step 6: Run the focused contract tests**

```powershell
python -m unittest tests.test_hm_api_contract.HmApiContractTest.test_reactor_contains_hm_api_before_cart tests.test_hm_api_contract.HmApiContractTest.test_hm_api_matches_hmall_java_11_baseline tests.test_hm_api_contract.HmApiContractTest.test_hm_api_is_a_non_runnable_openfeign_contract_jar tests.test_hm_api_contract.HmApiContractTest.test_contract_does_not_reference_cart_service
```

Expected: PASS for the four `hm-api` library tests; the cart integration test remains RED.

- [ ] **Step 7: Commit the library conversion**

```powershell
git add pom.xml hm-api tests/test_hm_api_contract.py
git commit -m "refactor: convert hm-api to shared contract library"
```

### Task 3: Make cart-service consume hm-api

**Files:**
- Modify: `cart-service/pom.xml`
- Modify: `cart-service/src/main/java/com/dongqh/cartservice/CartServiceApplication.java`
- Modify: `cart-service/src/main/java/com/dongqh/cartservice/service/impl/CartServiceImpl.java`
- Modify: `cart-service/src/test/java/com/dongqh/cartservice/CartServiceApplicationTests.java`
- Delete: `cart-service/src/main/java/com/dongqh/cartservice/client/ItemClient.java`
- Delete: `cart-service/src/main/java/com/dongqh/cartservice/domain/dto/ItemDTO.java`

**Interfaces:**
- Consumes: `com.heima:hm-api:1.0.0`, `com.dongqh.hmapi.client.ItemClient`, and `com.dongqh.hmapi.dto.ItemDTO`.
- Produces: a cart application that discovers `item-service` through Nacos and invokes the shared Feign client.

- [ ] **Step 1: Add the hm-api dependency**

```xml
<dependency>
    <groupId>com.heima</groupId>
    <artifactId>hm-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

Keep the existing explicit Nacos Discovery, OpenFeign, LoadBalancer, and `feign-okhttp` dependencies because they define `cart-service` runtime behavior.

- [ ] **Step 2: Scan the external Feign client package**

```java
@EnableFeignClients(basePackages = "com.dongqh.hmapi.client")
@MapperScan("com.dongqh.cartservice.mapper")
@SpringBootApplication
public class CartServiceApplication {
```

- [ ] **Step 3: Replace cart-local imports**

In `CartServiceImpl` and `CartServiceApplicationTests`, replace:

```java
import com.dongqh.cartservice.client.ItemClient;
import com.dongqh.cartservice.domain.dto.ItemDTO;
```

with:

```java
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.dto.ItemDTO;
```

- [ ] **Step 4: Delete duplicate cart contracts**

Delete `cart-service`'s `ItemClient.java` and `ItemDTO.java`; all consumers must compile against `hm-api`.

- [ ] **Step 5: Run the complete contract test**

```powershell
python -m unittest tests.test_hm_api_contract
```

Expected: all five tests PASS.

- [ ] **Step 6: Commit the cart integration**

```powershell
git add cart-service tests/test_hm_api_contract.py
git commit -m "refactor: consume item feign contract from hm-api"
```

### Task 4: Verify the reactor and runtime contract

**Files:**
- Test: `tests/test_hm_api_contract.py`
- Test: `tests/test_cart_service_maven_contract.py`
- Test: `cart-service/src/test/java/com/dongqh/cartservice/CartServiceApplicationTests.java`

**Interfaces:**
- Consumes: the completed root reactor and shared client contract.
- Produces: evidence that Java 11 bytecode and the cart OpenFeign integration build together.

- [ ] **Step 1: Run Python contracts**

```powershell
python -m unittest tests.test_hm_api_contract tests.test_cart_service_maven_contract tests.test_infrastructure_contract
```

Expected: all tests PASS.

- [ ] **Step 2: Run the Maven reactor build with JDK 17**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17.0.5'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl :cart-service -am clean test
```

Expected reactor order includes `hm-common`, `hm-api`, and `cart-service`; all modules report SUCCESS and cart tests report zero failures/errors.

- [ ] **Step 3: Check dependency direction and worktree integrity**

```powershell
mvn -pl :cart-service dependency:tree "-Dincludes=com.heima:hm-api"
git diff --check
```

Expected: `cart-service -> com.heima:hm-api:1.0.0:compile`, no whitespace errors, and no unrelated files altered by this implementation.
