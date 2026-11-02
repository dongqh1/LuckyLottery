# Service Package Root Unification Implementation Plan

> **For agentic workers:** This plan is executed inline in the current session. Each task updates one package family and keeps source paths aligned with declarations.

**Goal:** Give every business service one `com.dongqh.<business>` Java package root and correct package-based references.

**Architecture:** Keep each service's existing Java subpackage organization and Maven module boundary. Replace each service's legacy root in production and existing test sources, move Java files to paths that match their declared packages, then update explicit scanning configuration and compile all affected modules with tests skipped.

**Tech Stack:** Java 17, Spring Boot 4, Maven, PowerShell 7.

## Global Constraints

- Business service roots: cart=`com.dongqh.cart`, coupon=`com.dongqh.coupon`, fulfillment=`com.dongqh.fulfillment`, item=`com.dongqh.item`, lottery=`com.dongqh.lottery`, marketing=`com.dongqh.marketing`, membership=`com.dongqh.membership`, pay=`com.dongqh.pay`, points=`com.dongqh.points`, trade=`com.dongqh.trade`, user=`com.dongqh.user`.
- Keep existing subpackages such as `controller`, `service`, `domain`, `mapper`, `config`, and `messaging` under each new root.
- Preserve current HTTP routes, message contracts, persistence mappings, and business logic.
- Leave `hm-api`, `hm-gateway`, `hm-security`, and legacy `mall-backend` package roots unchanged.
- Preserve the user's existing edits in `coupon-service/src/main/java` while updating package-related lines and paths.
- Compile with `-DskipTests`; do not add or run tests as part of this migration.

---

### Task 1: Rebase services with one existing root

**Files:**
- Modify Java sources and existing tests in `cart-service/src/main/java/**/*.java` and `cart-service/src/test/java/**/*.java`.
- Modify Java sources and existing tests in `fulfillment-service/src/main/java/**/*.java` and `fulfillment-service/src/test/java/**/*.java`.
- Modify Java sources and existing tests in `lottery-service/src/main/java/**/*.java` and `lottery-service/src/test/java/**/*.java`.
- Modify Java sources and existing tests in `membership-service/src/main/java/**/*.java` and `membership-service/src/test/java/**/*.java`.
- Modify Java sources and existing tests in `pay-service/src/main/java/**/*.java` and `pay-service/src/test/java/**/*.java`.
- Modify Java sources and existing tests in `trade-service/src/main/java/**/*.java` and `trade-service/src/test/java/**/*.java`.
- Modify Java sources and existing tests in `user-service/src/main/java/**/*.java` and `user-service/src/test/java/**/*.java`.

- [ ] Replace each old package root with its mapped root in all Java files under the listed `src/main/java` and `src/test/java` directories.
- [ ] For each Java file, derive its destination path from the updated `package` declaration, preserve the filename, and move it within the same `src/main/java` or `src/test/java` tree.
- [ ] Update package-based configuration strings in these modules if the old root appears outside Java sources.

### Task 2: Normalize the split-root services

**Files:**
- Modify Java sources and existing tests in `coupon-service/src/main/java/**/*.java` and `coupon-service/src/test/java/**/*.java`.
- Modify Java sources and existing tests in `item-service/src/main/java/**/*.java` and `item-service/src/test/java/**/*.java`.
- Modify Java sources and existing tests in `points-service/src/main/java/**/*.java` and `points-service/src/test/java/**/*.java`.

- [ ] Map `com.dongqh.couponservice` and `com.dongqh.luckyhub` to `com.dongqh.coupon` within coupon-service Java sources and tests.
- [ ] Map `com.dongqh.itemservice` and `com.dongqh.luckyhub` to `com.dongqh.item` within item-service Java sources and tests.
- [ ] Map `com.dongqh.pointsservice` and `com.dongqh.luckyhub` to `com.dongqh.points` within points-service Java sources and tests.
- [ ] Derive each Java file's destination from its updated package declaration and move it within its existing source/test tree.
- [ ] Keep all existing service-layer and domain-layer package suffixes, including feature suffixes already present in declarations.
- [ ] Update package-based configuration strings in these modules if the old roots appear outside Java sources.

### Task 3: Rebase marketing-service and finish scanner configuration

**Files:**
- Modify Java sources and existing tests in `marketing-service/src/main/java/**/*.java` and `marketing-service/src/test/java/**/*.java`.
- Modify explicit scan configuration under `coupon-service/src/main`, `item-service/src/main`, `points-service/src/main`, and other listed service `src/main` trees wherever an old package string occurs.

- [ ] Map `com.dongqh.luckyhub` to `com.dongqh.marketing` in marketing-service Java sources and existing tests.
- [ ] Move each marketing-service Java file to the directory matching its updated package declaration.
- [ ] Update `@SpringBootApplication`, `@MapperScan`, `@ComponentScan`, `@EnableFeignClients`, exception-advice package filters, and properties/YAML/XML package strings to the new roots where present.
- [ ] Remove redundant explicit component-scan roots in an application class only when the default scan from the new application package already includes all module classes; retain any required mapper, Feign, or type-alias scans.

### Task 4: Check package references and compile affected modules

**Files:** all listed service `src/main`, `src/test`, and package-based configuration files.

- [ ] Search for the old roots `com.dongqh.cartservice`, `com.dongqh.couponservice`, `com.dongqh.fulfillmentservice`, `com.dongqh.itemservice`, `com.dongqh.lotteryservice`, `com.dongqh.luckyhub`, `com.dongqh.membershipservice`, `com.dongqh.payservice`, `com.dongqh.pointsservice`, `com.dongqh.tradeservice`, and `com.dongqh.userservice` in Java/XML/YAML/properties files.
- [ ] Confirm every Java file under each affected source tree has a package declaration whose path matches its location.
- [ ] Run `mvn -pl cart-service,coupon-service,fulfillment-service,item-service,lottery-service,marketing-service,membership-service,pay-service,points-service,trade-service,user-service -am -DskipTests test-compile` from the repository root.
- [ ] Confirm Maven compiles main and existing test sources without executing tests; fix package, import, or scanner errors and repeat the compile if needed.
- [ ] Review `git diff` and verify the six coupon-service files that were already modified retain their non-package edits.
