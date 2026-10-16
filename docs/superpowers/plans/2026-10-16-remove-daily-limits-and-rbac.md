# 删除每日抽奖限额与用户权限实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 删除两套抽奖实现的每日限额和全项目 RBAC，同时保留 JWT 登录、userId 身份上下文、本人数据边界与抽奖次数余额。

**Architecture:** 保留认证过滤器和可信用户上下文，把权限检查从所有业务服务边界移除。抽奖流程直接依赖 drawchance 预留，不再依赖日配额；抽奖锁使用独立的锁键实现。通过新增前向迁移删除运行时不再需要的字段和 RBAC 表。

**Tech Stack:** Java 21、Spring Boot 4、Spring MVC、OpenFeign、MyBatis-Plus、Flyway、Redis/Redisson、Maven。

## Global Constraints

- 使用 PowerShell 7 和 UTF-8 文件读写。
- 不修改历史 Flyway 迁移；用新迁移删除旧字段和表。
- 保留 JWT/Authorization、LoginContext/UserContext/TrustedUserContext 和 userId 数据归属校验。
- 保留 drawchance 表、服务、订单幂等、Outbox、奖励发放和抽奖锁。
- 不覆盖工作区中已有的无关未提交改动。

### Task 1: 移除共享权限基础设施和用户权限接口

**Files:**
- Delete: `hm-security/src/main/java/com/dongqh/hmsecurity/permission/*.java`（保留 `context/TrustedUserContext.java`）
- Delete: `hm-api/src/main/java/com/dongqh/hmapi/client/MarketingPermissionClient.java`
- Modify: `hm-api/src/main/java/com/dongqh/hmapi/client/UserClient.java`
- Delete: `user-service/src/main/java/com/dongqh/userservice/controller/InternalUserPermissionController.java`
- Delete: `user-service/src/main/java/com/dongqh/userservice/security/UserPermissionResolver.java`
- Modify: `user-service/src/main/java/com/dongqh/userservice/mapper/UserMapper.java`（移除权限角色查询方法）
- Modify: `user-service/src/test/**`（删除权限控制器测试）
- Modify: all `pom.xml` and application classes that import `hmsecurity.permission`

**Steps:**

- [ ] 删除权限包源文件和只覆盖权限行为的测试；保留可信用户上下文测试。
- [ ] 从 `UserClient` 删除 `getPermissionCodes`，从所有 Feign client registration 删除权限客户端。
- [ ] 删除用户服务权限解析端点、解析器和 Mapper 的角色查询方法；保留用户状态和登录方法。
- [ ] 从 `lottery-service`、`coupon-service`、`fulfillment-service`、`membership-service`、`points-service`、`marketing-service`、`user-service` 启动配置中删除权限 Bean、拦截器导入和权限客户端引用。
- [ ] 运行 `rg -n 'hmsecurity\.permission|RequirePermission|PermissionLookup|PermissionInterceptor|/internal/permissions'`，结果只允许出现在未修改的文档历史记录中；源码和测试不得命中。

### Task 2: 移除 lottery-service 每日限额

**Files:**
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/draworder/LotteryDrawService.java`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/service/draweligibility/DrawEligibilityService.java`
- Modify: activity DTO/VO/PO/mapper/service/controller files containing `dailyLimit`
- Delete: `lottery-service/src/main/java/com/dongqh/lotteryservice/quota/*.java`
- Delete: `lottery-service/src/main/resources/redis/lottery/*_draw_quota.lua`
- Modify: `lottery-service/src/main/java/com/dongqh/lotteryservice/lock/DrawLockService.java`
- Add: `lottery-service/src/main/java/com/dongqh/lotteryservice/lock/DrawLockKeys.java`
- Add: `lottery-service/src/main/resources/db/migration/V2__remove_daily_limit.sql`
- Modify/delete: quota and activity contract tests

**Steps:**

- [ ] Remove `dailyLimit` from activity commands, views, rows, query projections, SQL mappings and admin service writes.
- [ ] Change `EligibilitySnapshot` to carry only activity ID, no-win weight, prizes and snapshot time.
- [ ] Remove quota reservation/confirmation/release from `LotteryDrawService`, `LotteryEventConsumeService` and reconciliation; keep drawchance reserve/confirm/release and use snapshot date for drawchance.
- [ ] Move the lock key from `DrawQuotaKeys.drawLock` to `DrawLockKeys.drawLock`, preserving the exact key `draw:lock:{activityId}:{userId}`.
- [ ] Remove quota error codes and tests; add tests proving repeated draws are not rejected by a daily limit and drawchance behavior remains.
- [ ] Add Flyway `V2__remove_daily_limit.sql` dropping `chk_marketing_activity_daily_limit` and `daily_limit`.
- [ ] Run `mvn -pl lottery-service -am test`.

### Task 3: Remove marketing-service RBAC and daily quota

**Files:**
- Delete: `marketing-service/src/main/java/**/controller/rbac/**`
- Delete: `marketing-service/src/main/java/**/domain/annotation/rbac/**`
- Delete: `marketing-service/src/main/java/**/domain/cache/rbac/**`
- Delete: `marketing-service/src/main/java/**/domain/po/rbac/**`
- Delete: `marketing-service/src/main/java/**/mapper/rbac/**`
- Delete: `marketing-service/src/main/java/**/service/rbac/**` only when not used for user data scope
- Modify: all marketing controllers to remove permission imports/annotations while retaining `LoginContext.require()` and user ownership checks
- Modify: marketing auth payload/principal to remove role-only fields
- Delete: `marketing-service/src/main/java/**/domain/quota/lottery/**` and quota Lua scripts
- Modify: marketing lottery services/reconciliation/lock/activity DTO/VO/PO
- Add: `marketing-service/src/main/resources/db/migration/V29__remove_daily_limit_and_rbac.sql`
- Modify/delete: RBAC, quota, lottery activity contract tests

**Steps:**

- [ ] Remove permission annotations from every controller and delete permission interceptor configuration/lookup beans.
- [ ] Delete RBAC management endpoints, services, cache and persistence classes; keep `DataScopeService` only if it enforces userId ownership rather than roles.
- [ ] Remove the role field from JWT payload/principal construction and login response only where no authentication code needs it; preserve token parsing and user ID.
- [ ] Remove `dailyLimit` from activity command/view/entity and all lottery eligibility/quota code; preserve drawchance.
- [ ] Replace the marketing draw lock dependency with an independent lock key implementation.
- [ ] Add `V29__remove_daily_limit_and_rbac.sql` that drops the activity daily-limit constraint/column and RBAC tables in dependency order.
- [ ] Update demo/bootstrap SQL and frontend payloads so they do not insert or render `daily_limit` or role/permission data.
- [ ] Run `mvn -pl marketing-service -am test`.

### Task 4: Update cross-module bootstrap and verify removal

**Files:**
- Modify: `mysql/init/hmall.sql`, `mysql/migrations/**`, and any active schema snapshot containing daily limit/RBAC objects
- Modify: `nginx/html/hmall-portal/lottery.html`, `nginx/html/hmall-admin/marketing.html` only where they send/render removed fields
- Modify: affected test fixtures and API contract tests

**Steps:**

- [ ] Remove removed fields/tables from new-environment bootstrap SQL while leaving historical migration files intact.
- [ ] Remove front-end daily-limit form/display and permission-management UI calls.
- [ ] Run a source-only scan excluding `.git`, `target`, `.codex-progress`, `.hermes`, logs and historical docs:
  - `dailyLimit|daily_limit|DrawQuota|QuotaReservation|draw:quota|draw:reservation`
  - `RequirePermission|PermissionLookup|PermissionInterceptor|MarketingPermissionClient|/internal/permissions|sys_role|sys_permission`
- [ ] Run targeted authentication and user-ownership tests plus all affected module tests.
- [ ] Inspect `git diff --stat` and `git diff --check`; ensure no unrelated pre-existing changes were staged or reverted.

### Task 5: Final verification

**Steps:**

- [ ] Run the targeted Maven test commands again after all cross-module edits.
- [ ] Run `git diff --check` and source scans; record exact output.
- [ ] Report files changed, tests passed, and the inability to create `.git/index.lock` if it remains.
