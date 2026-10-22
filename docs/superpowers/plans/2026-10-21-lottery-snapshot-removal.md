# Lottery 抽奖与发奖快照移除实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 移除 lottery-service 持久化抽奖/发奖奖励快照，改为通过 prizeId 读取当前奖品和奖励定义，同时保留 Outbox、RabbitMQ、抽奖库存与幂等行为。

**Architecture:** 用不可变 Flyway V4 迁移删除配置副本列、索引和约束，并清理旧 Outbox 载荷中的奖励快照字段。抽奖事务只保存必要业务 ID；历史页面通过当前奖品关系填充原有展示字段；发奖消费者和实物领取解析当前奖励配置，超期回补通过当前奖品类型及稳定活动/奖品 ID 回补活动库存。请求内随机算法仍使用临时资格数据，但模型名称不再暗示持久化快照。

**Tech Stack:** Java 17、Spring Boot 4、MyBatis-Plus、Flyway/MySQL 8、RabbitMQ/Outbox、Jackson 3、Node.js 内置测试运行器。

## Global Constraints

- 仅删除 lottery-service 的抽奖/发奖奖励快照；保留订单快照、会员购买支付快照、履约收货地址快照及其它服务的快照。
- 保留 `marketing_prize.reward_definition_id` 当前配置关系、Outbox、RabbitMQ、消费幂等、抽奖订单、抽奖库存条件扣减和奖励状态机。
- V1、V2、V3 迁移视为已发布版本，不修改；新增 `V4__remove_lottery_reward_snapshots.sql`。
- API 的中奖记录和用户权益展示字段保持兼容，但由 `prizeId` 关联的当前奖品/奖励定义填充。
- 旧 Outbox 事件中的额外快照属性必须能被反序列化忽略；当前奖励无效时继续走现有隔离/失败路径。
- 不修改订单快照或履约地址快照相关文件；不构建或重启 Docker 服务。

---

## 文件结构与职责

- `lottery-service/src/main/resources/db/migration/V4__remove_lottery_reward_snapshots.sql`：删除奖励快照列和相关数据库对象，清理旧待投递事件载荷。
- `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/dto/prizecatalog/CreateRewardDefinitionCommand.java`、`domain/vo/prizecatalog/RewardDefinitionView.java`、`domain/po/prizecatalog/RewardDefinitionRow.java`：奖励定义管理 API 与实体不再暴露或映射 `config_snapshot`。
- `service/prizecatalog/RewardDefinitionAdminService.java`、`service/prizecatalog/PrizeCatalogService.java`、`domain/dto/prizecatalog/CreatePrizeCommand.java`：创建奖励时只保存当前目标、数量等结构化字段；不再规范化或传递自由 JSON。
- `nginx/html/hmall-admin/marketing.html`、`nginx/html/hmall-admin/js/marketing.js`：移除“奖励配置 JSON”输入与请求字段。
- `domain/model/draweligibility/EligibilitySnapshot.java`、`domain/model/drawtransaction/DrawPrizeSnapshot.java`、`algorithm/PrizeWeightSnapshot.java` 及其调用者：将请求内算法输入重命名为 `DrawEligibility`、`DrawPrizeCandidate`、`WeightedPrize`，移除 `RewardSnapshot`。
- `domain/po/drawtransaction/DrawRecordRow.java`、`UserBenefitRow.java`、`service/drawtransaction/DrawTransactionService.java`：实体不再映射持久化复制字段，中奖事务只保存必要业务身份。
- `mapper/drawtransaction/DrawRecordMapper.java`、`UserBenefitMapper.java`、`service/draworder/LotteryDrawQueryService.java`、`service/benefit/LotteryBenefitQueryService.java`：关联当前奖品和奖励定义以生成兼容的记录/权益展示字段与筛选结果。
- `outbox/event/PrizeFulfillmentRequestedEvent.java`、`reward/LotteryRewardIdentityVerifier.java`、`reward/ValidatedLotteryReward.java`、`reward/LotteryRewardDispatchService.java`：Outbox 事件只含 `benefitId`、`drawRecordId`、`prizeId`；校验稳定身份后解析当前奖励定义并发放。
- `service/benefit/LotteryPhysicalBenefitClaimService.java`、`service/benefit/BenefitClaimExpiryWorker.java`、`reward/LotteryFulfillmentStatusProjectionService.java`：按当前奖品关系处理实物领取；领取时检查商品当前库存不少于奖励数量；逾期回补按当前奖品类型和活动/奖品 ID 返还活动库存；履约状态按当前类型映射。
- `lottery-service/src/test/java/com/dongqh/lotteryservice/**`：更新合同测试并增加当前配置解析、老消息兼容和领取/回补/投影测试。
- `tests/test_lottery_admin_frontend.cjs`：确保管理页创建奖品时不再发送 JSON 快照字段。

## Task 1: 移除奖励定义配置 JSON 字段

**Files:**
- Create: `lottery-service/src/main/resources/db/migration/V4__remove_lottery_reward_snapshots.sql`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/dto/prizecatalog/CreateRewardDefinitionCommand.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/dto/prizecatalog/CreatePrizeCommand.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/vo/prizecatalog/RewardDefinitionView.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/po/prizecatalog/RewardDefinitionRow.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/prizecatalog/RewardDefinitionAdminService.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/prizecatalog/PrizeCatalogService.java`
- Modify: `nginx/html/hmall-admin/marketing.html`
- Modify: `nginx/html/hmall-admin/js/marketing.js`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/prizecatalog/PrizeCatalogContractTests.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/prizecatalog/PrizeCatalogSingleCommandFlowTests.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/prizecatalog/PrizeCatalogServiceTests.java`
- Test: `tests/test_lottery_admin_frontend.cjs`

**Interfaces:**
- `CreateRewardDefinitionCommand` keeps `rewardCode`, `rewardName`, `rewardType`, `targetId`, and `quantity`; it has no `configSnapshot` component.
- `RewardDefinitionView` keeps current structured reward fields, `status`, `createdAt`, and `updatedAt`; it has no `configSnapshot` component.
- `RewardDefinitionAdminService.create` validates target/type/quantity and persists no JSON field.
- Prize creation continues to submit one command containing display information and structured fulfillment information.

- [x] **Step 1: Add failing contracts**

  Update `PrizeCatalogContractTests` to assert `CreatePrizeCommand` and `CreateRewardDefinitionCommand` record components do not contain `configSnapshot`. Update `PrizeCatalogSingleCommandFlowTests` to assert the reward creator receives the five remaining values. Update `tests/test_lottery_admin_frontend.cjs` so the created prize payload has exactly the existing prize fields except `configSnapshot`; assert the page has no `奖励配置 JSON` label.

- [x] **Step 2: Run focused tests and confirm failure**

  Run:

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17.0.5'
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  & 'E:\maven\apache-maven-3.9.2-bin\apache-maven-3.9.2\bin\mvn.cmd' -pl lottery-service -am '-Dtest=PrizeCatalogContractTests,PrizeCatalogSingleCommandFlowTests,PrizeCatalogServiceTests' -Dsurefire.failIfNoSpecifiedTests=false test
  node --test tests/test_lottery_admin_frontend.cjs
  ```

  Expected: Java compilation or contract assertions fail because `configSnapshot` still exists; the frontend assertion fails because the form still submits the field.

- [x] **Step 3: Remove the field end to end**

  Remove the `configSnapshot` record components and PO mapping/getters/setters. Remove `ObjectMapper`, `normalizeJson`, and related JSON error imports from `RewardDefinitionAdminService`; construct `RewardDefinitionView` from the remaining structured fields. Update `PrizeCatalogService` to call `CreateRewardDefinitionCommand` with five arguments. Remove the textarea and its Vue form/payload/reset properties from `marketing.html` and `marketing.js`.

- [x] **Step 4: Run focused tests and confirm pass**

  Run the two commands from Step 2. Expected: all selected Java tests and the frontend Node tests pass.

## Task 2: Add the V4 schema and Outbox compatibility migration

**Files:**
- Modify: `lottery-service/src/main/resources/db/migration/V4__remove_lottery_reward_snapshots.sql`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/schema/LotterySchemaBoundaryTests.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/outbox/OutboxEventAndAppendTests.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/outbox/event/PrizeFulfillmentRequestedEvent.java`

**Interfaces:**
- `PrizeFulfillmentRequestedEvent` is `PrizeFulfillmentRequestedEvent(Long benefitId, Long drawRecordId, Long prizeId)` and validates each ID is positive.
- Jackson deserialization of a legacy event payload ignores extra fields, while the three required IDs remain required by the event constructor.

- [x] **Step 1: Add failing migration and compatibility tests**

  Extend `LotterySchemaBoundaryTests` to load V4 and assert it drops `reward_definition.config_snapshot`, all listed `lottery_draw_record`/`user_benefit` reward-copy columns and named checks/indexes, and uses `JSON_REMOVE` on old `PRIZE_FULFILLMENT_REQUESTED` payloads. Add an Outbox test that deserializes a legacy payload containing `prizeType`, `rewardDefinitionId`, `rewardType`, and `rewardFingerprint` into an event with the same three IDs.

- [x] **Step 2: Run tests and confirm failure**

  Run the focused schema and Outbox tests using the Maven command from Task 1 with `-Dtest=LotterySchemaBoundaryTests,OutboxEventAndAppendTests`. Expected: V4 is missing and the current event rejects legacy fields or still exposes snapshot components.

- [x] **Step 3: Implement V4 and the three-ID event**

  In V4, drop the named check constraints and indexes before their columns, then drop the specified snapshot columns. Use `JSON_REMOVE(payload, '$.payload.prizeType', '$.payload.rewardDefinitionId', '$.payload.rewardType', '$.payload.rewardFingerprint')` for `PRIZE_FULFILLMENT_REQUESTED` rows. Keep `marketing_prize.reward_definition_id` and `lottery_reward_quarantine.reward_definition_id`. Change the event record to the three IDs and add Jackson 3 `@JsonIgnoreProperties(ignoreUnknown = true)` so queued legacy envelopes remain consumable.

- [x] **Step 4: Run focused tests and confirm pass**

  Re-run the focused schema and Outbox tests. Expected: V4 contracts pass and the legacy payload keeps its IDs while ignored extra fields do not break deserialization.

## Task 3: Replace persisted draw and benefit copies with current catalog projections

**Files:**
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/po/drawtransaction/DrawRecordRow.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/po/drawtransaction/UserBenefitRow.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/drawtransaction/DrawTransactionService.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/mapper/drawtransaction/DrawRecordMapper.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/mapper/drawtransaction/UserBenefitMapper.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/draworder/LotteryDrawQueryService.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/benefit/LotteryBenefitQueryService.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/drawtransaction/DrawTransactionMapperContractTests.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/drawtransaction/UserBenefitModelContractTests.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/drawtransaction/DrawTransactionServiceTests.java`

**Interfaces:**
- Database writes to `lottery_draw_record` and `user_benefit` include only columns that remain after V4.
- Existing result/history/benefit API view fields are populated from `marketing_prize` and its current `reward_definition` relation.
- User benefit prize-type filtering uses the joined current `marketing_prize.prize_type` value.

- [x] **Step 1: Add failing mapping and query contracts**

  Update mapper contract tests so removed persistent fields are absent and retained display projections use `@TableField(exist = false)`. Assert draw history SQL joins `marketing_prize` and `reward_definition`; assert benefit page/count SQL filters current `p.prize_type`. Update draw transaction tests to assert only IDs and draw outcome are written and result display data comes from the candidate.

- [x] **Step 2: Run focused tests and confirm failure**

  Run with `-Dtest=DrawTransactionMapperContractTests,UserBenefitModelContractTests,DrawTransactionServiceTests,LotteryDrawQueryServiceTests,LotteryBenefitQueryServiceTests`. Expected: current row mappings and SQL still require columns dropped by V4.

- [x] **Step 3: Remove persistent copies and add current catalog joins**

  Remove persisted prize/reward snapshot mappings from row entities and transaction setup. Keep only transient result projection fields needed by existing response DTOs. Add `LEFT JOIN marketing_prize p ON p.id = r.prize_id` and `LEFT JOIN reward_definition rd ON rd.id = p.reward_definition_id` to draw record result queries, aliasing current prize name/type/image and reward type/target/quantity. Join the same tables in benefit page queries and replace `prize_type` predicates with `p.prize_type`. Keep count queries consistent with the same filter semantics.

- [x] **Step 4: Run focused tests and confirm pass**

  Re-run the tests from Step 2. Expected: writes compile without removed fields, mapper contracts reference current catalog joins, and existing API view tests pass with live catalog values.

## Task 4: Use request-local draw candidates without snapshot models

**Files:**
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/model/draweligibility/EligibilitySnapshot.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/model/drawtransaction/DrawPrizeSnapshot.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/algorithm/PrizeWeightSnapshot.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/model/drawtransaction/RewardSnapshot.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/model/drawtransaction/DrawExecutionContext.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/model/drawtransaction/DrawResultItem.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/draweligibility/DrawEligibilityService.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/algorithm/WeightedDrawEngine.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/draworder/LotteryDrawService.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/drawtransaction/DrawTransactionService.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/algorithm/WeightedDrawEngineTests.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/drawtransaction/DrawTransactionServiceTests.java`

**Interfaces:**
- `DrawEligibility` contains `activityId`, `noWinWeight`, `List<DrawPrizeCandidate> prizes`, and `evaluatedAt`.
- `DrawPrizeCandidate` contains request-local activity prize ID, prize ID, current display values, weight, remaining stock, enabled flag, and current structured reward configuration needed for the draw.
- `WeightedPrize` contains activity prize ID, prize ID, weight, remaining stock, and enabled flag.
- `RewardSnapshot` is deleted; no algorithm or result item serializes reward JSON or a reward fingerprint.

- [x] **Step 1: Add failing model and draw behavior tests**

  Update `WeightedDrawEngineTests` to construct `WeightedPrize` and cover enabled positive-stock candidates, disabled candidates, and depleted candidates. Update `DrawTransactionServiceTests` to assert draw result generation retains the selected current prize display fields while persisted row objects have no reward payload/fingerprint fields. Update `LotteryDrawService` tests to assert evaluation time still determines draw date and compensation timestamps.

- [x] **Step 2: Run focused tests and confirm failure**

  Run with `-Dtest=WeightedDrawEngineTests,DrawTransactionServiceTests,LotteryDrawServiceTests,DrawEligibilityServiceTests`. Expected: missing new types and current draw services still depend on `RewardSnapshot`.

- [x] **Step 3: Rename temporary models and remove reward JSON copies**

  Introduce `DrawEligibility`, `DrawPrizeCandidate`, and `WeightedPrize`; update method signatures and call sites. Keep one request-local `evaluatedAt` value for the existing draw date and compensation behavior. Pass current structured reward configuration in the runtime candidate only where the draw needs to construct immediate output, then delete `RewardSnapshot` and remove payload serialization/fingerprint generation from `DrawEligibilityService` and draw transaction/result models.

- [x] **Step 4: Run focused tests and confirm pass**

  Re-run the tests from Step 2. Expected: random selection, depletion behavior, draw date, compensation and immediate API response tests pass with no snapshot model dependency.

## Task 5: Resolve current reward configuration for async dispatch and fulfillment paths

**Files:**
- Create: `lottery-service/src/main/java/com/dongqh/lotteryservice/reward/LotteryCurrentRewardResolver.java`
- Create: `lottery-service/src/main/java/com/dongqh/lotteryservice/reward/CurrentLotteryReward.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/reward/LotteryRewardIdentityVerifier.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/reward/ValidatedLotteryReward.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/reward/LotteryRewardDispatchService.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/benefit/LotteryPhysicalBenefitClaimService.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/benefit/BenefitClaimExpiryWorker.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/reward/LotteryFulfillmentStatusProjectionService.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/outbox/service/LotteryEventConsumeService.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/reward/LotteryRewardIdentityVerifierTests.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/reward/LotteryRewardDispatchServiceTests.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/reward/LotteryFulfillmentStatusProjectionServiceTests.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/benefit/LotteryPhysicalBenefitClaimServiceTests.java`
- Test: `lottery-service/src/test/java/com/dongqh/lotteryservice/benefit/BenefitClaimExpiryWorkerTests.java`

**Interfaces:**
- `CurrentLotteryReward` carries the current prize and reward-definition rows resolved for one operation; it is not persisted or serialized as an event.
- `LotteryCurrentRewardResolver.resolve(long prizeId)` returns the enabled current prize/reward definition or raises the existing typed identity/target error consumed by the quarantine path.
- `ValidatedLotteryReward` carries stable event/order/record/benefit identity and status only; it has no reward snapshot property.
- `LotteryRewardIdentityVerifier.validate(...)` verifies event IDs and order/record/benefit ownership before dispatch; current definition validation happens through the resolver.

- [x] **Step 1: Add failing tests for current target behavior**

  Rewrite `LotteryRewardIdentityVerifierTests` to validate matching benefit/record/prize IDs and reject mismatched IDs without setting frozen fields. Add resolver/dispatch tests for current PRODUCT, COUPON, POINTS, MEMBERSHIP and DRAW_CHANCE configurations, missing or disabled current definitions, and a changed target being read at processing time. Add claim cases for current PRODUCT target and quantity/stock checks; add expiry cases for current benefit classification and stock return from stable draw IDs. Add fulfillment projection cases where type is obtained from the current prize relation.

- [x] **Step 2: Run focused tests and confirm failure**

  Run with `-Dtest=LotteryRewardIdentityVerifierTests,LotteryRewardDispatchServiceTests,LotteryFulfillmentStatusProjectionServiceTests,LotteryPhysicalBenefitClaimServiceTests,BenefitClaimExpiryWorkerTests`. Expected: current implementations compile only with frozen fields and the new resolver tests fail.

- [x] **Step 3: Resolve and dispatch from current prize configuration**

  Implement the resolver using the current `PrizeCatalogMapper`/`RewardDefinitionMapper` relation (`prizeId -> marketing_prize.reward_definition_id -> reward_definition`) and validate enabled status and reward/prize type compatibility. Refactor the identity verifier to check stable IDs only. Refactor dispatch to use current reward type, target ID, and quantity, then query the existing target clients where a task needs current target details. Refactor physical claim to resolve current PRODUCT data and check current item stock against reward quantity; let claim expiry validate current benefit classification and stable draw identity before returning activity prize stock without requiring an external item-service read. Refactor status projection to use current reward type. Route missing/disabled/incompatible definitions into existing quarantine/failed handling.

- [x] **Step 4: Run focused tests and confirm pass**

  Re-run the tests from Step 2. Expected: all five reward types use current definitions, physical claim checks current PRODUCT data and quantity, expiry returns activity prize stock from stable IDs, and unsupported or missing definitions follow existing quarantine/failure transitions.

## Task 6: Verify migration boundary, surviving snapshots, and full lottery behavior

**Files:**
- Modify only lottery-service source and tests listed above.
- Verify unchanged: trade/order snapshot files and fulfillment address snapshot files.

**Interfaces:**
- No production Java DTO/entity/service, Outbox event, or marketing admin UI carries `RewardSnapshot`, `rewardFingerprint`, or `configSnapshot`.
- Historical response DTO fields stay compatible and read current prize data.

- [x] **Step 1: Search the scoped source for removed paths**

  Run `rg -n "RewardSnapshot|rewardFingerprint|reward_fingerprint|reward_payload|configSnapshot|config_snapshot" lottery-service/src/main/java nginx/html/hmall-admin/marketing.html nginx/html/hmall-admin/js/marketing.js`. Expected: no hits in production Java or UI; the immutable V1 migration may retain original columns as historical schema, and V4 names them only to drop them.

- [x] **Step 2: Run the complete lottery-service test suite and frontend contract tests**

  Run `mvn -pl lottery-service -am test` with the configured JDK 17 and Maven 3.9.2; run `node --test tests/test_lottery_admin_frontend.cjs`. Expected: both complete successfully. If the known Mockito inline attach issue occurs on Windows, report the precise failing tests and separately run affected non-Mockito contracts; do not change application behavior to bypass the environment issue.

- [x] **Step 3: Confirm order and address snapshot files are untouched**

  Use `git diff --name-only` and verify no trade-service order snapshot or fulfillment-service address snapshot file is included in this task's changes. Existing unrelated working-tree changes from the prior lottery admin work remain intact.

- [x] **Step 4: Report the schema and rollout requirement**

  State that deployment must apply Flyway V4 before the new application code runs; existing frozen history will display current catalog values after migration, and pending fulfillment events will use current reward configuration. Do not build or restart Docker in this task.

