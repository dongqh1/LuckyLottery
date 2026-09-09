# LuckyHub 渐进式微服务拆分设计

## 1. 目标

在不丢失现有抽奖、营销资产和履约功能的前提下，将 `marketing-service` 中的 LuckyHub 单体拆分为：

- `lottery-service`：活动、奖池、抽奖、抽奖次数与抽奖可靠性；
- `marketing-service`：优惠券、积分、会员和商城营销结算；
- `fulfillment-service`：中奖权益、虚拟奖励发放和实物物流履约。

黑马商城已有的 `user-service`、`item-service`、`cart-service`、`trade-service` 和 `pay-service` 继续作为用户、商品、购物车、交易和支付的唯一事实来源。LuckyHub 不再维护重复的登录、用户、商品、现金订单、支付和收货地址主数据。

成功标准是：所有当前可用功能都有明确的新归属和回归验证；旧实现只能在替代链路通过验证并完成数据迁移后下线。

## 2. 已确定的技术基线

迁移期允许两套运行时并存：

- 黑马商城服务：Java 11、Spring Boot 2.7；
- LuckyHub 服务：Java 17、Spring Boot 4。

两套运行时统一使用以下工程与基础设施约定：

- Nacos 服务注册与发现；
- OpenFeign 服务间调用；
- MyBatis-Plus 数据访问；
- MySQL 独立数据库；
- Redis、Redisson 和 Redis Stream；
- Maven 聚合构建和统一模块命名；
- Nginx 作为浏览器统一入口；
- RS256 JWT 和 `Authorization` 请求头透传；
- Outbox、幂等消费、重试、隔离和对账补偿。

迁移期不让 LuckyHub 直接依赖 Spring Boot 2.7 的 `hm-common` 自动配置。新建 Java 17 的 `luckyhub-common`，仅供三个 LuckyHub 服务复用认证、异常、响应、请求 ID 和基础配置。未来全体升级 Java 17 / Spring Boot 4 后，再将 `hm-common` 与 `luckyhub-common` 合并。

`hm-api` 保持 Java 11 可消费的纯契约 JAR，只包含 DTO、OpenFeign Client 和 Feign 配置，不包含 Controller、数据库、Swagger UI 或业务实现。契约不得暴露 `javax.servlet`、`jakarta.servlet`、MyBatis Entity 或某个服务的内部异常类型，从而允许两套运行时共同使用。

## 3. 迁移策略比较

### 方案 A：一次性拆分并切换

一次性建立三个服务、拆库、替换所有调用并删除旧代码。最终结构形成较快，但任一链路失败都会阻断整体上线，难以判断功能是拆分前就异常还是迁移引入异常。

### 方案 B：先拆包，再一次性拆库

先在当前单体内部整理依赖，完成后一次性建立三个服务和数据库。代码风险低于方案 A，但数据库和运行流量仍存在一次性切换风险。

### 方案 C：模块化单体加渐进式绞杀迁移

先建立契约和边界测试，让旧接口继续提供服务；逐个接入黑马主数据和新服务；按功能迁移数据与流量；验证通过后删除旧轨。该方案步骤较多，但每一步都可回滚，最符合“功能不缺失”的要求。

本设计采用方案 C。

## 4. 最终服务边界

### 4.1 user-service

唯一负责：

- 用户账号、密码和状态；
- RS256 JWT 签发；
- `USER`、`ADMIN` 角色；
- 用户收货地址主数据。

LuckyHub 只保存外部 `userId` 和业务发生时的必要快照，不创建用户投影、不自动创建营销用户，也不保存商城密码。

### 4.2 item-service

唯一负责：

- 商品主数据；
- 商品上下架状态；
- 商城真实库存；
- 商品详情查询和库存扣减。

营销侧可以维护“活动奖池额度”或“兑换活动额度”，但字段和接口必须明确命名为 `quota`，不得伪装成第二套商品库存。如果营销商品与商城共享实物库存，必须通过 `ItemClient` 预占、确认或释放。

### 4.3 trade-service 与 pay-service

`trade-service` 唯一负责现金商城订单、订单明细和商城订单物流状态；`pay-service` 唯一负责支付单和支付状态。

交易创建时调用营销服务查询会员折扣并预占优惠券；支付成功后写入 Outbox，向营销服务发布 `OrderPaidEventV1`。LuckyHub 不再提供 `/api/orders`、`/api/payments` 或支付模拟接口。

### 4.4 lottery-service

负责：

- 活动创建、发布、禁用和状态刷新；
- 奖品、奖池、权重和活动奖池额度；
- 单抽、十连抽、抽奖请求幂等；
- 抽奖资格和每日配额；
- 抽奖次数账户、预留、确认、释放和流水；
- Redisson 用户/活动锁；
- Redis Lua 配额预留、确认和释放；
- MySQL 条件扣减活动奖池额度；
- 抽奖订单、抽奖记录和奖励快照；
- 抽奖 Outbox、Redis Stream 发布和对账补偿。

抽奖事务成功后发布 `LotteryRewardCreatedEventV1`，不直接写优惠券、积分、会员、权益或物流数据库。

### 4.5 marketing-service

负责：

- 优惠券模板、发放、查询、锁定、核销、释放和过期；
- 商品、分类、品牌优惠券范围；
- 积分账户、不可变流水、调整、反向流水和积分兑换；
- 基于黑马 `itemId` 的积分兑换活动；
- 会员产品、用户会员和会员发放记录；
- 会员折扣查询；
- 消费 `OrderPaidEventV1` 发放积分及营销奖励。

积分兑换活动拥有的是营销额度 `availableQuota`。真实商品库存仍由 `item-service` 决定。

### 4.6 fulfillment-service

负责：

- 用户中奖权益记录；
- 奖励发放任务、尝试记录、重试和隔离；
- 消费 `LotteryRewardCreatedEventV1`；
- 调用 `marketing-service` 发放优惠券、积分和会员；
- 调用 `lottery-service` 发放抽奖次数；
- 实物权益领取期限；
- 从 `user-service` 读取地址并保存不可变加密快照；
- 奖品发货单、物流轨迹、回调验签、脱敏查询和异常处理。

模拟优惠券、积分、会员和物流 Gateway 只允许在 `dev`、`test` Profile 注册。生产 Profile 必须使用真实 Feign Gateway，缺少真实实现时应启动失败，不能静默使用模拟器。

## 5. 代码迁移清单

| 当前包 | 新归属 | 处理方式 |
|---|---|---|
| `activity` | `lottery-service` | 完整迁移 |
| `prize` | `lottery-service` | 完整迁移 |
| `lottery` | `lottery-service` | 保留抽奖、Outbox、Stream 发布和对账；奖励消费移出 |
| `drawchance` | `lottery-service` | 完整迁移并提供幂等发放接口 |
| `reward` | `lottery-service` | 保留定义、校验和快照；Dispatch 移到履约服务 |
| `inventory/mapper`、`inventory/service` | `lottery-service` | 改名为活动奖池额度和额度流水 |
| `coupon` | `marketing-service` | 完整迁移 |
| `points` | `marketing-service` | 保留账户、流水和黑马商品兑换路径；删除旧 SKU 路径 |
| `membership` | `marketing-service` | 完整迁移 |
| `benefit` | `fulfillment-service` | 迁移权益记录、查询和状态投影 |
| `fulfillment` | `fulfillment-service` | 完整迁移 |
| `shipping` | `fulfillment-service` | 保留快照、领取、发货和轨迹；删除本地地址簿 |
| `integration/hmall` | `hm-api` 加各服务 Adapter | 将 `RestClient` 调用改成 Feign Client |
| `integration/gateway` | `fulfillment-service` | 保留端口，生产实现改为 Feign |
| `integration/simulator` | `fulfillment-service` 测试支持 | 只在 `dev`、`test` 注册 |
| `catalog` | 删除 | 由 `item-service` 替代 |
| `order` | 删除 | 由 `trade-service` 替代 |
| `payment` | 删除 | 由 `pay-service` 替代 |
| `inventory/channel` | 删除 | 旧迷你商城库存路径下线 |
| `auth` | 精简 | 保留黑马 JWT 验证和上下文；删除本地登录、密码、Session |
| `rbac` | 精简 | 迁移期保留注解、权限码、拦截器和角色映射；删除本地 RBAC CRUD 与表 |
| `common`、`config` | `luckyhub-common` 或对应服务 | 按职责迁移，禁止服务间复制 |

## 6. 数据所有权

### hm_lottery

- `marketing_activity`
- `marketing_activity_prize`
- `marketing_prize`
- `reward_definition`
- `lottery_draw_order`
- `lottery_draw_record`
- `draw_chance_account`
- `draw_chance_ledger`
- `draw_chance_reservation`
- `lottery_outbox`
- `lottery_consume_record`
- `lottery_reward_quarantine`
- 活动奖池额度流水

### hm_marketing

- `coupon_template`
- `user_coupon`
- `coupon_issue_record`
- `points_account`
- `points_ledger`
- `points_redemption_offer`
- `points_redemption_order`
- `membership_product`
- `user_membership`
- `membership_grant_record`
- `marketing_order_event_consume_record`

### hm_fulfillment

- `user_benefit`
- `fulfillment_task`
- `fulfillment_attempt`
- `fulfillment_quarantine`
- `shipping_address_snapshot`
- `shipping_order`
- `shipping_tracking_event`
- `shipping_callback_receipt`
- `fulfillment_event_consume_record`

三个数据库禁止外键指向其他服务数据库。跨服务关联只保存外部 ID、事件版本、业务号和必要快照。

原 `luckyhub` 数据库的 V1 至 V25 Flyway 文件不得修改。新服务从各自的 V1 baseline 开始建库；迁移脚本复制数据并保存旧 ID，完成数量、金额、状态和幂等键核对后再切换流量。

## 7. 服务调用与事件

同步调用使用 OpenFeign：

- `trade-service -> marketing-service`：会员折扣、优惠券试算、预占、确认、释放；
- `lottery-service -> item-service`：商品奖励定义校验和商品快照；
- `marketing-service -> item-service`：积分兑换商品校验与库存操作；
- `fulfillment-service -> user-service`：读取本人地址；
- `fulfillment-service -> item-service`：实物商品校验；
- `fulfillment-service -> marketing-service`：幂等发放优惠券、积分和会员；
- `fulfillment-service -> lottery-service`：幂等发放抽奖次数。

异步调用沿用 Outbox 和 Redis Stream：

- `OrderPaidEventV1`：商城支付完成，营销服务发放订单积分或抽奖次数；
- `LotteryRewardCreatedEventV1`：抽奖奖励已冻结，履约服务创建权益和发放任务；
- `PointsRedemptionCreatedEventV1`：实物积分兑换已扣除营销额度和积分，履约服务创建地址快照与发货单；
- `FulfillmentStatusChangedEventV1`：可选，用于抽奖结果和权益页面投影状态。

事件必须包含：`eventId`、`eventType`、`eventVersion`、`occurredAt`、`traceId`、`businessNo`、`userId` 和负载。消费者以 `eventId + consumerName` 建唯一键，业务写入以 `businessNo` 建唯一键。

`LotteryRewardCreatedEventV1` 必须携带不可变奖励快照和稳定的 `benefitNo`。抽奖接口仍立即返回中奖结果；权益发放状态允许短暂显示为 `PENDING`，但已有数字 `benefitId` 接口在迁移期继续保留。履约服务完成权益投影后，旧 ID 与新 `benefitNo` 的映射必须可查询，确保 `/benefits/{id}/claim` 等现有页面和接口不失效。

## 8. 认证、授权和接口兼容

`user-service` 是唯一 Token 签发方。Nginx 原样转发 `Authorization`；各服务只验证 RS256 签名、过期时间、`user` 和 `role`。

迁移期保留现有浏览器路径：

- `/api/marketing/lottery/**` 路由到 `lottery-service`；
- `/api/marketing/coupons/**`、`points/**`、`memberships/**` 路由到 `marketing-service`；
- `/api/marketing/benefits/**`、`shipping/**` 路由到 `fulfillment-service`。

外部响应结构和字段在切流期间保持兼容。服务间接口使用明确的内部路径和 DTO，不使用浏览器响应包装类作为 Feign 契约。

管理员权限迁移期仍按黑马 `ADMIN` 角色映射全部后台权限，普通用户只获得本人数据权限。所有用户数据查询必须同时带可信 `userId` 条件，不能只依赖前端参数。

## 9. 可靠性和失败处理

- 抽奖配额：Redis Lua 原子预留，MySQL 为最终事实，对账任务回收超时预留；
- 活动奖池额度：SQL 条件更新，禁止先查后扣；
- 事件：本地事务写 Outbox，Relay 租约领取，成功确认，失败指数退避；
- 奖励履约：至少一次投递加业务幂等，结果未知时查询下游状态，不能直接重发；
- 优惠券：预占、使用、释放均为 CAS 状态迁移；
- 积分：账户余额加不可变流水，冲正产生反向流水，不更新原流水；
- 实物地址：请求时从 `user-service` 读取本人地址，保存加密不可变快照；
- 物流回调：验签、时间窗、回调幂等和合法状态机；
- 跨服务调用：设置连接、读取超时；只有幂等读或带业务号的写允许重试；
- 生产环境不允许自动回退到本地模拟 Gateway。

## 10. 实施阶段和回滚门禁

### 阶段 1：黑马微服务运行基线

让 Compose 启动 item、cart、user、trade、pay，Nacos 注册正常；清理 `hm-api`；旧 `mall-service` 暂时作为回滚路径。通过商城登录、商品、购物车、下单和支付回归后，浏览器流量切向五服务。

### 阶段 2：统一身份和商城主数据

LuckyHub 全部使用黑马 JWT、用户 ID、商品和地址接口。先停止调用旧登录、用户投影、本地商品和本地地址接口，再删除对应代码和表。

### 阶段 3：删除迷你商城旧轨

积分兑换切到 `offerId + hmallItemId`；现金订单切到 `trade-service`；支付切到 `pay-service`；活动奖池额度与商品库存分离。每条新链路通过后删除 `catalog`、`order`、`payment` 和 `inventory/channel`。

### 阶段 4：抽取 lottery-service

先移动代码并保持旧数据库访问，再建立 `hm_lottery` baseline 和数据迁移，最后切换 Nginx 路由。验证抽奖并发、幂等、配额、Outbox、Stream 和对账后停止旧抽奖 Bean。

### 阶段 5：抽取 fulfillment-service

建立事件契约和消费幂等，迁移权益、履约与物流表，启用真实 Feign Gateway。验证五类奖励、领取期限、地址快照、物流回调、失败重试和隔离后切换路由。

### 阶段 6：营销库独立与最终清理

将剩余优惠券、积分和会员数据迁入 `hm_marketing`，删除 LuckyHub 本地认证/RBAC CRUD、模拟生产 Bean、旧 Controller、旧表访问和兼容构造器。原 `luckyhub` 数据库保留只读观察期后再人工归档。

每个阶段必须满足以下门禁才能进入下一阶段：

1. 新增契约测试先失败，再由实现使其通过；
2. 对应服务单元与集成测试通过；
3. 全量工作区契约测试通过；
4. 全新数据库从零迁移通过；
5. 核心页面和 API 冒烟通过；
6. 数据迁移核对通过；
7. 已记录明确的回滚路由和数据处理方式。

## 11. 功能不缺失验收矩阵

最终验收至少覆盖：

- 用户和管理员使用黑马账号登录并访问各自接口；
- 活动创建、奖品配置、奖池配置、发布、禁用、恢复和自动状态刷新；
- 单抽、十连抽、重复 `requestId`、同用户并发、每日配额和额外抽奖次数；
- 无奖、实物、优惠券、积分、会员、抽奖次数六类结果；
- Outbox Relay、Redis Stream Pending 重领、重复事件消费和超时对账；
- 优惠券列表、试算、预占、订单绑定、核销、释放和锁定过期；
- 会员折扣参与商城下单和支付金额校验；
- 支付成功积分发放和重复支付事件幂等；
- 积分账户、流水、兑换活动、兑换、库存/额度不足和冲正；
- 实物权益领取、黑马地址所有权、加密快照、过期返还活动额度；
- 发货、物流轨迹、回调验签、重复回调、失败重试、终止和数据脱敏；
- 浏览器 `lottery.html`、`points.html`、`coupons.html`、`benefits.html`、订单确认和营销后台页面；
- Nacos 注册、Feign 调用、认证头透传、Nginx 路由和服务独立启动；
- 三个 LuckyHub 数据库之间不存在跨库外键和直接跨库查询。

## 12. 为统一升级 Java 17 / Spring Boot 4 保留的约束

- 所有跨服务契约与 Servlet、ORM 和具体 JSON 实现解耦；
- Java 11 服务不向契约暴露旧日期、旧分页或 `javax` 类型；
- 配置键、服务名、接口路径和事件版本在升级时保持稳定；
- `hm-common` 不再承载具体业务；
- 新基础代码优先构造器注入，不使用字段注入；
- 清除旧兼容构造器和测试专用生产入口；
- 待五个黑马服务升级后，用统一的 Java 17 `platform-common` 替换两个 Common 模块，不改变业务 API。

## 13. 非目标

本次拆分不新增真实支付渠道、真实快递供应商、退款、退货、售后、复杂组织权限、独立消息中间件或 Kubernetes。它们不属于现有功能保全范围，不能阻塞当前微服务拆分。
