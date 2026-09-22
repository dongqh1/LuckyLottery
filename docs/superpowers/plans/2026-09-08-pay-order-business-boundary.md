# Pay Order Business Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every `PayOrder`/`PayOrderMapper` dependency from `trade-service` and synchronize pending payment amounts through a business-level OpenFeign API owned by `pay-service`.

**Architecture:** `hm-api` exposes `PayClient.syncPendingAmount(Long, Long, Integer)`. `trade-service` calls it with the already validated order user after recalculating an order total. `pay-service` validates ownership and payment status, locks matching `pay_order` rows, and updates their amount inside its own local transaction; `PayOrder` and `PayOrderMapper` never cross the service boundary. The explicit user id is temporary until the gateway and Feign identity propagation are implemented.

**Tech Stack:** Java 11, Spring Boot 2.7.12, Spring Cloud OpenFeign, MyBatis-Plus, JUnit/Python contract tests, Maven.

## Global Constraints

- Preserve the user's existing uncommitted changes.
- Do not commit or stage Git changes.
- Keep `PayOrder` and `PayOrderMapper` exclusively in `pay-service`.
- Do not introduce a shared payment PO in `hm-api`.

---

### Task 1: Define and consume the payment business contract

**Files:**
- Create: `hm-api/src/main/java/com/dongqh/hmapi/client/PayClient.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/service/impl/OrderServiceImpl.java`
- Test: `tests/test_pay_order_business_boundary.py`

**Interfaces:**
- Produces: `void PayClient.syncPendingAmount(Long orderId, Long userId, Integer amount)`
- Consumes: Nacos service name `pay-service` and endpoint `PUT /pay-orders/biz/{orderId}/pending-amount?amount=...`

- [ ] Write a failing contract test asserting the Feign method exists and `trade-service` has no `PayOrder`, `PayStatus`, `PayOrderMapper`, or `payOrderMapper` references.
- [ ] Run `python -B -m unittest tests.test_pay_order_business_boundary -v` and verify it fails because `PayClient` is absent and direct Mapper usage remains.
- [ ] Add `PayClient`; inject it through `OrderServiceImpl`'s constructor; replace direct payment-row queries and updates with `payClient.syncPendingAmount(orderId, order.getTotalFee())`.
- [ ] Re-run the contract test and verify it passes.

### Task 2: Implement the pay-service endpoint and local transaction

**Files:**
- Modify: `pay-service/src/main/java/com/dongqh/payservice/controller/PayController.java`
- Modify: `pay-service/src/main/java/com/dongqh/payservice/service/IPayOrderService.java`
- Modify: `pay-service/src/main/java/com/dongqh/payservice/service/impl/PayOrderServiceImpl.java`
- Test: `tests/test_pay_order_business_boundary.py`

**Interfaces:**
- Consumes: `Long orderId`, validated `Long userId`, and `Integer amount`.
- Produces: an idempotent local payment synchronization operation; no matching payment row is a successful no-op.

- [ ] Extend the failing contract test to require controller delegation and a transactional service implementation with a locking query, ownership/status validation, and local `PayOrder` updates.
- [ ] Run the test and verify the pay-service assertions fail.
- [ ] Replace the unfinished controller fragment with the endpoint, add `syncPendingAmount` to `IPayOrderService`, and implement it using the local `PayOrderMapper` through `baseMapper`/MyBatis-Plus.
- [ ] Re-run the contract test and verify it passes.

### Task 3: Build verification

**Files:**
- Verify only; no planned source changes.

**Interfaces:**
- Consumes: the current `hm-api` artifact in the reactor/local Maven repository.
- Produces: compiler evidence for `hm-api`, `trade-service`, and `pay-service`.

- [ ] Run `mvn -pl :hm-api -am -DskipTests install` with JDK 17 compiling the Java 11 target.
- [ ] Run `mvn -f trade-service/pom.xml -DskipTests compile`.
- [ ] Run `mvn -f pay-service/pom.xml -DskipTests compile`.
- [ ] Run the full payment-boundary contract test and report any unrelated remaining compilation errors without expanding scope silently.
