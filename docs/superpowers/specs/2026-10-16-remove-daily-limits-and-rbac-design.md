# 删除每日抽奖限额与用户权限设计

## 目标

移除项目中两套抽奖实现的每日抽奖限额，以及全项目角色/权限/RBAC 功能；保留登录认证、JWT/Authorization、用户身份上下文、基于 userId 的数据归属校验和独立抽奖次数余额。

## 范围

### 保留

- 登录、JWT 解析、Authorization 校验、LoginContext、UserContext、TrustedUserContext。
- 业务服务基于 userId 的本人数据访问约束。
- drawchance 抽奖次数账户、流水、预留、确认、释放。
- 抽奖锁、订单幂等、库存扣减、奖励发放、Outbox 和必要的对账。
- sys_user 用户表及用户状态、密码和登录相关能力。

### 删除

- lottery-service 与 marketing-service 的 dailyLimit 字段、校验、接口字段和 Redis 日配额预占/确认/释放。
- quota 包、日配额 Lua 脚本、日配额键、日配额超时集合和日配额对账分支。
- hm-security 中权限注解、权限查找、权限拦截器、权限异常和权限编码；保留可信用户上下文。
- 所有服务控制器上的 RequirePermission 注解及对应 MVC 配置。
- user-service 的权限解析器和 /internal/permissions/** 接口。
- hm-api 的权限 Feign 方法及 MarketingPermissionClient。
- marketing-service 的 RBAC 控制器、角色/权限服务、权限缓存、RBAC 实体和 Mapper。
- 登录载荷中仅为 RBAC 使用的 role 字段；保留 userId、username、sessionId/token。
- RBAC 数据表 sys_user_role、sys_role_permission、sys_permission、sys_role；保留 sys_user。

## 行为设计

删除权限校验后，原受权限保护的接口仍由现有认证链路识别用户，但不再根据角色或权限编码拒绝请求。业务层已有的 userId 数据归属校验继续执行。

删除每日限额后，活动创建、更新、详情和列表不再携带 dailyLimit。抽奖流程使用当前活动快照时间计算 drawDate，仅用于抽奖次数预留和抽奖订单记录；drawchance 余额仍按现有语义消费。

抽奖互斥锁从 quota 命名空间中独立出来，使用 `draw:lock:{activityId}:{userId}`，避免锁实现依赖已删除的 quota 包。

## 数据迁移

不修改历史 Flyway 文件。新增前向迁移：

- lottery-service：删除 marketing_activity.daily_limit 及其检查约束。
- marketing-service：删除 marketing_activity.daily_limit 及其检查约束；删除 RBAC 关联表和角色/权限表，保留 sys_user。
- 同步更新新环境使用的初始化/快照 SQL，避免重新创建删除的字段和表。

迁移不删除抽奖次数相关表，也不清理用户身份数据。

## 兼容与错误处理

- 删除 dailyLimit 相关错误码、错误分支和 quota 预留失败补偿；抽奖次数预留失败仍按现有错误语义处理。
- 删除权限查询失败、权限缺失和权限拦截异常路径；保留认证失败和未登录错误。
- 既有订单、抽奖次数预留、Outbox 消息和奖励事件不依赖日配额状态，迁移后仍可完成确认、释放和对账。

## 验收

- 活动 API、DTO、VO、PO、Mapper 和数据库不再出现 dailyLimit/daily_limit。
- 源码不再出现 RequirePermission、PermissionLookup、PermissionInterceptor、权限 Feign、RBAC 控制器和 /internal/permissions。
- 源码不再出现 DrawQuotaService、QuotaReservation、日配额 Lua、draw:quota 或 draw:reservation；保留独立抽奖锁和 drawchance。
- JWT 登录、用户身份上下文、本人数据访问、单抽/十连抽、幂等、抽奖次数余额和奖励发放测试通过。
- Flyway 迁移后，活动表无 daily_limit，RBAC 表不存在，sys_user 和 draw_chance_* 表存在。
- 只提交本次功能涉及的文件；工作区已有的其他未提交改动不被覆盖或回滚。
