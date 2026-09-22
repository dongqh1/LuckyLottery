# Trade Payment Order Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `pay-service` consume trade orders only through a payment snapshot DTO and business-level TradeClient operations, with no dependency on the trade `Order` PO or database locking API.

**Architecture:** `trade-service` remains the sole owner of `Order` and `OrderMapper`. `hm-api` exposes `OrderPaymentDTO` plus query/conditional-pay-success methods in `TradeClient`. `pay-service` reads the snapshot and asks trade-service to perform a guarded status transition; it never attempts to hold a trade database lock across HTTP.

**Tech Stack:** Java 11, Spring Boot 2.7.12, OpenFeign, MyBatis-Plus, Maven, Python architecture contract tests.

## Global Constraints

- Preserve all existing uncommitted user changes.
- Do not stage or commit Git changes.
- Do not place `Order`, `OrderMapper`, or MyBatis annotations in `hm-api`.
- Keep existing temporary explicit user-id parameters until gateway identity propagation exists.
- Both `trade-service` and `pay-service` must compile before completion.

---

### Task 1: Add the shared payment snapshot and TradeClient contract

**Files:**
- Create: `hm-api/src/main/java/com/dongqh/hmapi/dto/OrderPaymentDTO.java`
- Modify: `hm-api/src/main/java/com/dongqh/hmapi/client/TradeClient.java`
- Test: `tests/test_trade_payment_order_boundary.py`

**Interfaces:**
- Produces: `OrderPaymentDTO TradeClient.queryOrderForPayment(Long orderId, Long userId)`
- Produces: `void TradeClient.markOrderPaySuccess(Long orderId, Long userId, Integer amount)`

- [ ] Write a failing contract test for the DTO fields and Feign mappings.
- [ ] Run the test and verify it fails because the DTO and methods are absent.
- [ ] Add the DTO and both TradeClient methods without persistence annotations.
- [ ] Re-run the test and verify the contract passes.

### Task 2: Implement trade-service payment endpoints

**Files:**
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/controller/OrderController.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/service/IOrderService.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/service/impl/OrderServiceImpl.java`
- Test: `tests/test_trade_payment_order_boundary.py`

**Interfaces:**
- Consumes: order id, validated user id, and expected amount.
- Produces: an owned order payment snapshot and an atomic conditional transition from unpaid to paid.

- [ ] Extend the failing test for controller delegation, snapshot conversion, and guarded status update.
- [ ] Add `queryOrderForPayment`; reject missing/wrong-owner orders.
- [ ] Replace the unconditional pay-success update with a MyBatis-Plus conditional update on id, user id, amount, and unpaid status.
- [ ] Re-run the contract test.

### Task 3: Refactor pay-service away from Order PO

**Files:**
- Modify: `pay-service/src/main/java/com/dongqh/payservice/service/impl/PayOrderServiceImpl.java`
- Modify: `hm-api/src/main/java/com/dongqh/hmapi/client/UserClient.java`
- Test: `tests/test_trade_payment_order_boundary.py`

**Interfaces:**
- Consumes: `OrderPaymentDTO` from `TradeClient`.
- Produces: payment application and balance payment flows with no `Order` symbol, `lockOrder`, or remote `lambdaUpdate` usage.

- [ ] Extend the test to forbid trade persistence types and locking/update APIs in pay-service.
- [ ] Replace both `Order` reads with `queryOrderForPayment`.
- [ ] Replace `markBusinessOrderPaid` with `TradeClient.markOrderPaySuccess`.
- [ ] Add the existing `/users/money/deduct` operation to `UserClient` so the refactored pay service compiles.
- [ ] Re-run the contract test.

### Task 4: Remove copied cross-module package references and verify builds

**Files:**
- Modify: `pay-service/src/main/java/com/dongqh/payservice/marketing/HttpCouponCheckoutClient.java`
- Modify: `pay-service/src/main/java/com/dongqh/payservice/marketing/HttpMembershipBenefitClient.java`
- Create: `pay-service/src/main/java/com/dongqh/payservice/utils/AuthorizationContext.java`
- Verify: Maven builds and Python contract tests.

**Interfaces:**
- Produces: pay-local marketing adapters with no `trade-service` package dependency.

- [ ] Write failing assertions that pay-service source contains no `com.dongqh.tradeservice` imports.
- [ ] Point adapters to pay-local interfaces/views and add the pay-local authorization context used by both adapters.
- [ ] Install `hm-api`, compile `trade-service`, compile `pay-service`, and run related contract tests.
- [ ] Report exact verification results and any runtime concerns without committing.
