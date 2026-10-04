# Points 兑换与 Fulfillment 服务契约设计

日期：2026-09-29
状态：方案已由用户确认；代码切片已实现并完成 JDK 编译
目标：按现有五个业务微服务的做法，尽快完成 Points 兑换服务接线

## 1. 设计原则

以 item-service、cart-service、user-service、trade-service、pay-service 作为新服务的实现模板：

- 新模块继续当前 JDK 17 / Spring Boot 4 根 Maven reactor 和服务配置；复用旧五个服务已有的共享契约与组件，不复制旧父 POM 或切换框架版本。
- 跨服务 DTO 与 Feign Client 放在现有 hm-api；直接复用 hm-common / hm-security 中已有的通用响应和可信用户上下文能力，不在新服务重复实现。
- 复用现有服务发现、错误码和异常返回模式；远程写入失败时明确报错，不用空对象或成功降级。
- 不新增通用 Saga 框架、工作流表、消息总线、Seata 全局事务或新的基础设施。

Points 兑换由 Points 服务通过同步 Feign 调用 Item 和 Fulfillment。兑换单自身已有的 PROCESSING 状态加一个当前处理步骤字段，用于幂等重试和失败补偿。正常请求等所需服务完成本地持久化后返回 COMPLETED。

本设计只推进代码边界；Marketing 仍是旧写入方。身份/RBAC 与目录/库存审计、历史数据核对和逐域验收完成前，不切换 Gateway/Nginx 路由、不回填数据、不改账号。

## 2. 复用现有组件与领域所有权

| 服务/共享模块 | 本流程职责 |
| --- | --- |
| points-service | 账户、不可变流水、兑换单、Points offer 配额及兑换步骤 |
| item-service | 商品/SKU 快照与实际 SKU 渠道库存 |
| fulfillment-service | User 地址快照、发货单、物流任务和现有 Outbox |
| user-service | 用户资格和地址簿 |
| hm-api | Item、User、Fulfillment 的 Feign 接口及请求/响应 DTO |
| hm-common / hm-security | 沿用现有通用响应和用户上下文；不复制一份实现 |

Points 继续使用已经接好的 User 资格只读接口。Points 不读 Item、User 或 Fulfillment 数据库，不保存明文收件信息。Fulfillment 根据 userId 与 addressId 调用 User 并在本服务保存地址快照，只向 Points 返回脱敏字段。

普通 SKU 使用 Item 的 POINTS 渠道库存。Points offer 的 availableStock 继续归 Points 所有，本轮不增加 Points offer 与 Item 实际库存之间的新联动。COMPLETED 表示积分和库存操作完成且物流任务已由 Fulfillment 持久接受，不表示已发货或签收。已有关联发货单的兑换继续沿用当前规则，不允许直接冲正。

## 3. 内部接口

以下接口通过现有服务发现和 Feign 调用，只作为内部服务接口，不新增 Gateway/Nginx 公开路由。DTO 放入 hm-api，调用方沿用现有客户端异常处理风格。

### Item 渠道库存

在现有 ItemClient 上补齐按 redemptionNo 幂等的预留、确认、释放和返还操作，不再新建客户端框架。预留请求字段：

    reservationNo: <redemptionNo>
    skuId: 123
    channelCode: POINTS
    quantity: 1

相同业务号和相同 SKU/渠道/数量重试时返回当前预留状态；请求内容不一致时返回冲突。服务端条件更新库存，所有写接口重复调用安全。积分不足时释放未确认的预留；确定失败且库存已确认时执行返还。

### Fulfillment 发货单

在现有 Fulfillment 服务中增加轻量内部 Controller/DTO，调用已有地址快照和 ShippingOrderService，不重写物流领域逻辑。创建请求字段：

    sourceType: POINTS_REDEMPTION
    sourceId: <pointsRedemptionOrderId>
    targetUserId: 456
    addressId: 789
    hmallItemId: 321
    skuCode: SKU-1
    productName: 兑换时的商品名
    imageUrl: https://example.invalid/item.png
    quantity: 1

hmallItemId 可为空。sourceId 使用 Points 兑换单 ID，延续现有 POINTS_REDEMPTION 来源标识。Fulfillment 在自己的本地事务中校验并复制地址、创建发货单、创建 LOGISTICS 任务并写入现有 Task Ready Outbox。以 sourceType + sourceId 幂等：相同快照返回原发货单，不同快照按现有冲突错误处理。

响应返回发货单 ID/编号、Fulfillment 编号、当前发货状态和脱敏地址摘要，不含地址明文。增加按发货单 ID 批量读取接口，让 Points 查询和分页保持原有 shippingOrderId、shippingNo、shippingStatus 视图，避免远程 N+1 查询。

创建成功以 Fulfillment 数据库已持久化发货单、任务和 Outbox 为准，不等待 RabbitMQ 消费回执。消息开关关闭时任务仍可持久排队；现有 Fulfillment Worker/Scheduler 条件不变。

## 4. 最小兑换流程

Points 继续提供现有 /api/points/redemptions 请求和响应语义：

1. 校验用户资格并读取 Item 或 offer 商品快照。
2. 以唯一 redemptionNo 在 Points 本地保存兑换单、请求指纹和商品快照，状态为 PROCESSING；该行 ID 用作 Fulfillment 的来源 ID。
3. 普通 SKU 通过 Item 以 redemptionNo 预留库存；Points offer 则在 Points 本地扣减 offer 配额。
4. Points 本地事务按 redemptionNo 幂等扣分并写入不可变流水。
5. 普通 SKU 确认 Item 预留。
6. 实物商品调用 Fulfillment 创建发货单；虚拟商品跳过。
7. 所有适用操作完成后标记 Points 兑换为 COMPLETED 并返回原兼容视图。

请求指纹只包含用户、SKU 或 offer、数量和地址 ID。目录名称、价格、SKU 码和图片在创建兑换记录时固定，重试复用原快照，不因目录变化而覆盖。

## 5. 幂等与失败处理

- 同一 redemptionNo 和相同请求命中 PROCESSING 时，按保存的步骤继续；请求不同则返回幂等冲突。
- 每个远程写操作都带稳定业务键。同一请求超时后可用原键安全重试；接口返回已提交的当前结果。幂等冲突或状态不一致时停止自动补偿，保留记录供检查。
- Item 预留明确失败时不扣积分，隐藏终态失败单并返回原业务错误。
- 预留成功但扣分明确失败时释放库存。扣分或库存确认完成后 Fulfillment 明确拒绝创建时，冲正积分并返还库存；Points offer 分支同时恢复 offer 配额。
- 补偿失败时保留兑换单为 PROCESSING，当前处理步骤指向未完成的补偿；同一请求重试会继续补偿，不会重复扣分、释放或创建履约。
- 补偿完成后记录 COMPENSATED 内部终态及原错误码，并从用户查询和分页结果隐藏失败单；同一 redemptionNo 重放返回原业务错误。
- Fulfillment 已接受物流任务后，之后的物流失败继续由 Fulfillment 现有重试与隔离逻辑处理，不自动退款。用户视图中的物流状态由 Fulfillment 批量读取接口提供。

不增加后台工作流调度器。当前切片用同步调用和同号重试完成恢复；真正开放新服务写流量前，需确认 PROCESSING 记录可被发现和重试。

## 实施记录

- 用户确认采用本地 Saga、幂等调用与补偿；补偿成功后返回原错误并隐藏失败单，补偿未完成时保留 PROCESSING。
- 已补 Points 兑换订单与逐步恢复编排、Item POINTS 库存接口、Fulfillment 实物订单接口及批量状态查询；Points offers 的配额仍由 Points 自己管理。
- 兑换重试复用已保存的商品和地址请求身份；未知远程结果保留 PROCESSING，确定失败按步骤补偿。无后台调度器。
- 身份/RBAC、目录/库存审计、历史数据核对与逐域验收仍是写流量和路由切换门槛；SQL、Compose、容器和应用未运行。
- JDK 17 多模块 `clean compile -DskipTests` 已通过；本切片未运行测试。

## 6. 实施和验收

代码切片限定为：

1. 在 points-service 按旧 Points 业务模型补兑换单、兑换 offer 与 API，复用现有账户/流水及 User 资格适配器。
2. 在 hm-api 扩充 Item 库存和 Fulfillment 发货 Feign 契约。
3. 在 item-service 增加最小库存内部操作。
4. 在 fulfillment-service 增加薄 Controller，复用现有地址快照、发货单、物流任务和 Outbox。

使用隔离 JDK 17 定向验证覆盖同号并发、同请求重放、不同请求冲突、积分不足、库存不足、履约失败补偿、补偿重试、虚拟 SKU、Points offer 和脱敏查询。不会启动 Compose、容器或应用，也不运行需要 Nacos/真实数据库的上下文验证。

本设计不授权执行 SQL、迁移历史数据、改账号、切路由、停用 Marketing 写入方，或变更 Lottery Redis Lua、配额键、锁、TTL 与对账。十服务代码拆分完成后，仍须独立完成身份/RBAC 与目录/库存审计和切流验收。


- Review follow-up (2026-09-29): same-redemption side effects and step transitions now execute under a short Points order-row lock; compensation/completion require the expected step. Manual SKU reversal records its stable reversal number before Item work for same-key recovery; local Points-offer reversal is atomic. Disabled users can still receive a reversal. Independent review closed all prior findings; JDK 17 offline Points reactor compile passed. Tests and runtime were not run.
