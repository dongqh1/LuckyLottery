# Shared OrderDetailDTO Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `hm-api` the single source of `OrderDetailDTO` for trade/item stock calls and repair the directly related Feign and `OrderServiceImpl` references.

**Architecture:** Both caller and item-service use `com.dongqh.hmapi.dto.OrderDetailDTO`; duplicate module-local definitions are removed. `ItemClient` remains the HTTP boundary, while payment Mapper usage is explicitly unchanged.

**Tech Stack:** Java 11 target, Spring Boot 2.7, Spring Cloud OpenFeign, Maven, Python unittest

## Global Constraints

- Do not modify payment Mapper/client behavior.
- Do not fix unrelated trade-service compilation errors.
- Do not create Git commits or stage files.
- Preserve all unrelated dirty-worktree changes.

---

### Task 1: Add the shared DTO contract test

**Files:**
- Create: `tests/test_shared_order_detail_dto_contract.py`

- [ ] Assert that only `hm-api` defines `OrderDetailDTO`, trade/item Java files import the hm-api type, `ItemClient` uses `/items/stock/deduct`, and the two known `OrderServiceImpl` residual references are gone.
- [ ] Run `python -m unittest tests.test_shared_order_detail_dto_contract -v` and confirm RED before production edits.

### Task 2: Make hm-api DTO the single contract

**Files:**
- Modify: `hm-api/src/main/java/com/dongqh/hmapi/dto/OrderDetailDTO.java`
- Delete: `trade-service/src/main/java/com/dongqh/tradeservice/domain/dto/OrderDetailDTO.java`
- Delete: `item-service/src/main/java/com/dongqh/itemservice/domain/dto/OrderDetailDTO.java`
- Modify: `item-service/pom.xml`

- [ ] Keep only Lombok `@Data`, `@Accessors(chain = true)`, `itemId`, and `num` in the shared DTO; remove Swagger coupling.
- [ ] Add the existing `com.heima:hm-api:1.0.0` dependency to item-service.
- [ ] Remove the two duplicate DTO source files.

### Task 3: Update consumers and direct residual references

**Files:**
- Modify: `hm-api/src/main/java/com/dongqh/hmapi/client/ItemClient.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/domain/dto/OrderFormDTO.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/controller/OrderController.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/service/IOrderService.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/service/impl/OrderServiceImpl.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/marketing/CouponCheckoutClient.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/marketing/HttpCouponCheckoutClient.java`
- Modify: `item-service/src/main/java/com/dongqh/itemservice/controller/ItemController.java`
- Modify: `item-service/src/main/java/com/dongqh/itemservice/service/IItemService.java`
- Modify: `item-service/src/main/java/com/dongqh/itemservice/service/impl/ItemServiceImpl.java`
- Modify: `item-service/src/main/java/com/dongqh/itemservice/mapper/ItemMapper.java`

- [ ] Replace local DTO imports/usages with `com.dongqh.hmapi.dto.OrderDetailDTO`.
- [ ] Change the Feign stock path to `/items/stock/deduct`.
- [ ] Change `itemService.queryItemByIds(ids)` to `itemClient.queryItemByIds(ids)`.
- [ ] Change `buildLogistics(Long, Address)` to `buildLogistics(Long, AddressDTO)`.
- [ ] Leave all `PayOrderMapper` and payment code unchanged.

### Task 4: Verify focused behavior and report remaining unrelated errors

- [ ] Run `python -m unittest tests.test_shared_order_detail_dto_contract tests.test_hm_api_contract -v` and require all tests to pass.
- [ ] With JDK 17 executing Java 11 target, run `mvn -pl :item-service -am -DskipTests compile`.
- [ ] Attempt a focused trade-service compile only if its Maven configuration resolves; report pre-existing/out-of-scope errors separately.
- [ ] Run `git diff --check` and inspect the scoped file list.
- [ ] Do not commit or stage any file.
