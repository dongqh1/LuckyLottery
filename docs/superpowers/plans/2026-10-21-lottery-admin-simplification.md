# 抽奖管理简化实施计划（历史版本）

> 本计划早于快照移除决策，涉及奖励快照的步骤已被 [Lottery 抽奖与发奖快照移除设计](../specs/2026-10-21-lottery-snapshot-removal-design.md) 取代；当前实现以该后续设计为准。

> **For agentic workers:** 按任务逐项完成，每项先写测试、确认测试按预期失败，再实现并验证。任务在当前会话内顺序执行。

**Goal:** 将奖励与奖品创建收敛为一个奖品表单，并增加管理员按用户管理奖励抽奖机会的页面和接口。

**Architecture:** 管理端向奖品接口一次提交展示信息与奖励发放信息；后端事务内创建 `reward_definition` 和 `marketing_prize`，现有抽奖快照及 Outbox/RabbitMQ 不变。抽奖机会管理通过 lottery-service 的受限管理员接口读账户/流水，并在 MySQL 行锁事务内调整余额、写带操作人和原因的流水。

**Tech Stack:** Spring Boot MVC、MyBatis-Plus/MyBatis、Flyway、MySQL、Vue 2、Element UI、Node.js `node:test`。

## Global Constraints

- 不恢复每日抽奖次数上限。
- 不使用 Redis Lua 调整人工奖励抽奖机会。
- 保留 `reward_definition` 内部模型和现有履约/Outbox/RabbitMQ 消息语义。
- 保留活动奖池的权重、库存和展示顺序配置。
- 仅将新抽奖机会管理接口限制为配置白名单管理员；本地种子用户 ID 为 `1`，部署可由 `LOTTERY_ADMIN_USER_IDS` 覆盖。
- 保护并保留工作区现有未提交修改。

---

## 文件结构与职责

- `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/dto/prizecatalog/CreatePrizeCommand.java`: 单次创建奖品的请求字段，含奖励类型、目标、数量和可选配置。
- `lottery-service/src/main/java/com/dongqh/lotteryservice/service/prizecatalog/PrizeCatalogService.java`: 一个事务内生成奖励定义并创建奖品。
- `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/vo/prizecatalog/PrizeView.java`: 给管理页返回完整奖品与发放摘要，不暴露操作所需的奖励 ID。
- `lottery-service/src/main/java/com/dongqh/lotteryservice/controller/RewardDefinitionAdminController.java`: 删除独立管理路由；内部奖励创建由奖品服务调用。
- `lottery-service/src/main/java/com/dongqh/lotteryservice/controller/DrawChanceAdminController.java`: 管理端账户、流水和人工调整 API。
- `lottery-service/src/main/java/com/dongqh/lotteryservice/service/drawchance/DrawChanceAdminService.java`: 账户查询、分页流水、幂等人工增减和账户行锁事务。
- `lottery-service/src/main/java/com/dongqh/lotteryservice/security/LotteryAdminAuthorization.java`: 从可信 `UserContext` 读取用户 ID 并核对管理员白名单。
- `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/dto/drawchance/AdjustDrawChanceCommand.java`: 调整 ID、方向、数量和原因。
- `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/vo/drawchance/DrawChanceLedgerView.java`: 管理员流水响应。
- `lottery-service/src/main/java/com/dongqh/lotteryservice/mapper/drawchance/DrawChanceLedgerMapper.java`: 按用户分页查询流水和总数。
- `lottery-service/src/main/java/com/dongqh/lotteryservice/domain/po/drawchance/DrawChanceLedgerRow.java`: 增加操作人和原因列映射。
- `lottery-service/src/main/resources/application.yml`、`compose.yaml`、`hm-gateway/src/main/resources/application.yaml`: 配置管理员用户 ID 白名单并将管理 API 路由到 lottery-service。
- `lottery-service/src/main/resources/db/migration/V3__add_draw_chance_admin_audit.sql`: 对流水表增加可空操作人和原因字段，保留旧记录。
- `nginx/html/hmall-admin/marketing.html`、`js/marketing.js`、`css/marketing.css`: 合并奖品表单，增加抽奖机会管理面板。
- `tests/test_lottery_admin_frontend.cjs`: 覆盖单表单工作流、机会查询和调整请求。
- `lottery-service/src/test/java/com/dongqh/lotteryservice/prizecatalog/PrizeCatalogSingleCommandFlowTests.java`: 覆盖单次请求创建内部奖励定义、奖品及发放摘要。
- `lottery-service/src/test/java/com/dongqh/lotteryservice/drawchance/DrawChanceAdminServiceTests.java`: 覆盖增减、余额不足、幂等冲突、审计字段和账户行锁行为。
- `lottery-service/src/test/java/com/dongqh/lotteryservice/security/LotteryAdminAuthorizationTests.java`: 覆盖白名单管理员和普通用户拒绝。

## Task 1: 将奖励与奖品创建合并为一个后端操作

**Files:** 上述 DTO、PrizeCatalogService、PrizeView、PrizeAdminController、RewardDefinitionAdminController、网关路由和奖品服务测试。

**接口：** `POST /api/admin/prizes` 接收 `prizeName, rewardType, prizeLevel, imageUrl, description, stackable, targetId, quantity, configSnapshot`；后端从 `rewardType` 派生 `prizeType`。响应包含奖品 ID、展示字段、奖励类型、目标 ID 和数量。创建与更新请求/响应均不暴露 `rewardDefinitionId`；更新时保留已有的内部奖励定义关联。

- [x] 在奖品创建测试覆盖单次请求生成内部奖励定义和奖品、PRODUCT 映射 PHYSICAL、目标/数量摘要及奖励编码。
- [x] 先运行 `PrizeCatalogContractTests`，确认旧 DTO 因缺少发放字段和仍包含奖励 ID 而失败。
- [x] 扩展 `CreatePrizeCommand` 加入奖励发放字段；从 `RewardType` 派生 `PrizeType`，校验目标和数量。
- [x] 在 `PrizeCatalogService.create` 中生成唯一奖励编码（`PRIZE-` 加 UUID），调用 `RewardDefinitionAdminService.create`，随后写入关联奖品；保留事务边界和现有目标校验。
- [x] 扩展 `PrizeView` 包含奖励目标和数量，并从管理响应中移除内部奖励 ID。
- [x] 更新 `UpdatePrizeCommand`，移除手动绑定奖励 ID 的字段；更新奖品时保留现有内部关联并检查奖励类型兼容。
- [x] 删除独立 `RewardDefinitionAdminController` 及 Gateway 中 `/api/admin/reward-definitions/**` 路由；保留内部奖励服务、表和发奖关联。
- [x] 运行 `PrizeCatalogSingleCommandFlowTests,PrizeCatalogContractTests`，确认创建和请求/响应契约通过。

## Task 2: 增加抽奖机会管理员授权和账户/流水查询

**Files:** 新建 `LotteryAdminAuthorization`、`DrawChanceAdminController`、`DrawChanceAdminService`、`DrawChanceLedgerView`；修改 `application.yml`、`compose.yaml`、`DrawChanceLedgerMapper`、`DrawChanceLedgerRow`、`V3__add_draw_chance_admin_audit.sql`；新增授权与管理服务测试。

**接口：**

- `GET /api/admin/draw-chances/users/{userId}` → `DrawChanceAccountView`
- `GET /api/admin/draw-chances/users/{userId}/ledger?page=1&size=20` → `PageResponse<DrawChanceLedgerView>`
- `POST /api/admin/draw-chances/users/{userId}/adjustments`，请求为 `adjustmentId, direction(CREDIT|DEBIT), amount, reason`，返回账户余额和新流水。

- [x] 写授权测试：`UserContext` 用户 ID 在 `luckyhub.lottery.admin-user-ids` 时允许；普通用户、缺失身份、无效配置时拒绝。
- [x] 新增配置属性读取 `LOTTERY_ADMIN_USER_IDS`；默认开发白名单为 `1`，部署可覆盖。授权失败返回 HTTP 403。
- [x] 写服务测试：增加/扣除、reserved 不变、余额不足、调整幂等和冲突、操作人及原因流水。
- [x] 添加 Flyway V3 两列 `operator_user_id BIGINT UNSIGNED NULL` 与 `reason VARCHAR(255) NULL`；旧流水保持 NULL。
- [x] 在管理员服务事务中验证用户有效、锁定/创建账户、校验 adjustmentId 幂等、检查余额、更新账户并插入 MANUAL_ADJUSTMENT 流水；扣除只使用 available balance。
- [x] 为流水 mapper 添加用户条件的降序分页和 count 查询；上限 `size <= 100`。
- [x] 新增 controller，所有端点先调用管理员白名单校验，调整请求校验正数金额、非空原因及最大长度。
- [x] 运行 `DrawChanceAdminServiceTests,LotteryAdminAuthorizationTests`，确认授权和账户行为通过。

## Task 3: 合并管理页表单并加入抽奖机会操作

**Files:** `marketing.html`、`js/marketing.js`、`css/marketing.css`、`tests/test_lottery_admin_frontend.cjs`。

- [x] 修改前端测试：一个奖品请求、机会账户/流水查询、加减请求、幂等重试、余额不足和非管理员提示。
- [x] 运行前端测试，确认旧页面流程和新管理方法缺失时按预期失败。
- [x] 将两步奖励/奖品流程改为单个奖品表单，调用 `/lottery/admin/prizes`。
- [x] 在营销管理页面加入抽奖机会面板：显示余额、分页流水和增减原因表单，每次操作带 `adjustmentId`。
- [x] 请求 `/lottery/admin/draw-chances/users/{userId}`、`/ledger` 和 `/adjustments`；成功后刷新账户与流水；分别提示余额不足和 403。
- [x] 移除旧的 reward-definition 前端请求、对话框和奖励 ID 展示；保留活动奖池配置。
- [x] 运行 `node --test tests/test_lottery_admin_frontend.cjs`，确认页面契约通过。

## Task 4: 集成验证

- [ ] 运行 `mvn -pl lottery-service -am test`；本机 Mockito/Surefire fork 因 Windows attach 管道异常未能完成，需在正常 Java 测试环境复跑。
- [x] 运行 `mvn -pl lottery-service -am -Dtest=PrizeCatalogSingleCommandFlowTests,PrizeCatalogContractTests,DrawChanceAdminServiceTests,LotteryAdminAuthorizationTests -Dsurefire.failIfNoSpecifiedTests=false test`。
- [x] 运行 `node --test tests/test_lottery_admin_frontend.cjs`。
- [x] 检查 `git diff --check`；保留工作区里本任务开始前已有的其他未提交修改。
- [x] 核对 Flyway V3 列定义、Gateway 管理接口路由和 `compose.yaml` 白名单环境变量映射；未重建或重启 Docker。

