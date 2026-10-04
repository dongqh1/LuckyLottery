# hm-security 可信用户上下文抽取实施计划

> **For agentic workers:** Execute the steps in order and verify each test result before continuing.

**Goal:** 将商城服务共享的可信用户 ID ThreadLocal 状态集中到非部署型 `hm-security` JAR，同时保留现有 `hm-common.UserContext` API。

**Architecture:** 新增无 Spring、无数据库依赖的 `TrustedUserContext`，负责当前线程的用户 ID 设置、读取和清理。`hm-common.UserContext` 保留现有全限定类名与方法签名，作为委托到新上下文的兼容门面；现有拦截器、Feign 转发和用户服务不改调用方式。

**Tech Stack:** Java 17、Maven、JUnit 5。

## Global Constraints

- 根工程使用 Java 17 和 Spring Boot 4.0.8。
- `hm-security` 是共享 Java JAR，不是可部署服务；本切片不加入权限查找、JWT 解析、Servlet Filter、Gateway/Nginx 路由或数据库访问。
- 保持 `com.hmall.common.utils.UserContext.setUser(Long)`、`getUser()`、`removeUser()` 的公开签名与调用兼容。
- 不迁移或修改任何账号、权限、Marketing 表、Flyway V1–V28 或现有登录行为。
- 不启动 Compose 或其他状态化服务；抽奖 Redis Lua、配额键、Redisson 锁、TTL 与对账逻辑保持不变。

---

### Task 1: 建立 hm-security 可信用户上下文

**Files:**
- Modify: `pom.xml`
- Create: `hm-security/pom.xml`
- Create: `hm-security/src/main/java/com/dongqh/hmsecurity/context/TrustedUserContext.java`
- Test: `hm-security/src/test/java/com/dongqh/hmsecurity/context/TrustedUserContextTests.java`

**Interfaces:**
- Produces: `TrustedUserContext.setUserId(Long)`, `TrustedUserContext.getUserId()`, `TrustedUserContext.clear()`。
- The context uses one `ThreadLocal<Long>` and has no application or persistence dependencies.

- [x] **Step 1: Add the Maven module metadata**

Add `<module>hm-security</module>` to the root `<modules>` list before `mall-backend`, then create this exact POM:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.dongqh</groupId>
        <artifactId>hmall-lottery</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>hm-security</artifactId>
    <name>hm-security</name>
    <description>Shared trusted user context for HMall services</description>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [x] **Step 2: Write context behavior tests**

Create `TrustedUserContextTests.java` with these exact cases:

```java
package com.dongqh.hmsecurity.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrustedUserContextTests {

@BeforeEach
void clearBeforeTest() {
    TrustedUserContext.clear();
}

@AfterEach
void clearAfterTest() {
    TrustedUserContext.clear();
}

@Test
void returnsNullWhenNoUserIsSet() {
    assertNull(TrustedUserContext.getUserId());
}

@Test
void returnsTheUserIdSetOnTheCurrentThread() {
    TrustedUserContext.setUserId(42L);

    assertEquals(42L, TrustedUserContext.getUserId());
}

@Test
void clearRemovesTheCurrentThreadUserId() {
    TrustedUserContext.setUserId(42L);

    TrustedUserContext.clear();

    assertNull(TrustedUserContext.getUserId());
}
}
```

Create the package directory before saving the test.

- [x] **Step 3: Run the tests and confirm the missing implementation is detected**

Run: `mvn -pl hm-security test`

Expected: test compilation fails because `TrustedUserContext` has not been implemented yet.

- [x] **Step 4: Implement the minimal context**

Create `TrustedUserContext.java` with this implementation:

```java
package com.dongqh.hmsecurity.context;

public final class TrustedUserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private TrustedUserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
```

- [x] **Step 5: Verify the module tests**

Run: `mvn -pl hm-security test`

Expected: all three context tests pass.

### Task 2: Preserve hm-common UserContext compatibility

**Files:**
- Modify: `mall-backend/hm-common/pom.xml`
- Modify: `mall-backend/hm-common/src/main/java/com/hmall/common/utils/UserContext.java`
- Test: `mall-backend/hm-common/src/test/java/com/hmall/common/utils/UserContextBridgeTests.java`

**Interfaces:**
- Consumes: `com.dongqh.hmsecurity.context.TrustedUserContext` from Task 1.
- Preserves: `com.hmall.common.utils.UserContext` and its existing static method signatures.

- [x] **Step 1: Add the shared-module dependency and failing bridge tests**

Add this dependency to `mall-backend/hm-common/pom.xml`:

```xml
<dependency>
    <groupId>com.dongqh</groupId>
    <artifactId>hm-security</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Create `UserContextBridgeTests.java`:

```java
package com.hmall.common.utils;

import com.dongqh.hmsecurity.context.TrustedUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserContextBridgeTests {

    @BeforeEach
    @AfterEach
    void clearContexts() {
        UserContext.removeUser();
        TrustedUserContext.clear();
    }

    @Test
    void legacyFacadeWritesToSharedContext() {
        UserContext.setUser(17L);

        assertEquals(17L, TrustedUserContext.getUserId());
    }

    @Test
    void sharedContextIsReadableThroughLegacyFacade() {
        TrustedUserContext.setUserId(23L);

        assertEquals(23L, UserContext.getUser());
    }
}
```

- [x] **Step 2: Run the bridge tests and confirm they fail on separate storage**

Run: `mvn -pl :hm-common -am test`

Expected: the new bridge tests fail because the old `UserContext` still owns a separate `ThreadLocal`.

- [x] **Step 3: Delegate the legacy facade**

Replace `mall-backend/hm-common/src/main/java/com/hmall/common/utils/UserContext.java` with this compatibility facade:

```java
package com.hmall.common.utils;

import com.dongqh.hmsecurity.context.TrustedUserContext;

public class UserContext {

    public static void setUser(Long userId) {
        TrustedUserContext.setUserId(userId);
    }

    public static Long getUser() {
        return TrustedUserContext.getUserId();
    }

    public static void removeUser() {
        TrustedUserContext.clear();
    }
}
```

- [ ] **Step 4: Verify compatibility and consumers**

Run: `mvn -pl :hm-common -am test`

Expected: all `hm-common` tests, including the new bridge tests, pass.

Observed: passed; `hm-common` ran 9 tests with no failures.

Run: `mvn -pl :hm-api,:user-service -am test`

Expected: existing Feign and user-service tests pass without source call-site changes.

Observed: compilation and `UserAdminServiceTest` (7 tests) passed. `UserServiceApplicationTests.contextLoads` failed because Seata attempted to discover an unavailable Nacos registry while starting the Spring test context. Do not rerun this context-starting test while stateful-service startup is out of scope; use compile-only or isolated tests for additional consumer verification.

- [x] **Step 5: Verify the existing identity/RBAC audit contract**

Run: `python -B -m unittest tests.test_marketing_identity_rbac_preflight`

Expected: all four audit contract tests pass. No database connection is attempted.

## Self-Review

- This plan changes only Java request-context storage and its compatibility facade; it does not migrate users, permissions, or Marketing authentication.
- The old API remains source-compatible for `hm-api`, `user-service`, and existing consumers.
- The implementation has no servlet lifecycle hooks; existing `UserInfoInterceptor.afterCompletion` remains responsible for clearing request context.
- Tests cover unset, set/read, clear, legacy-to-shared, and shared-to-legacy behavior.
