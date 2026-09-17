# 履约任务 RabbitMQ 可靠触发设计

## 目标与范围

本阶段只迁移履约任务的正常触发入口。创建履约任务时，在同一个本地数据库事务中写入履约 Outbox；Relay 将任务就绪事件可靠发布到 RabbitMQ；营销服务消费者按履约编号执行指定任务。

`fulfillment_task` 继续作为履约事实和状态机的唯一数据源，保留状态、租约、执行次数、退避时间、隔离记录和人工恢复数据。管理端创建、查询、人工重试和终止接口继续同步返回。发券、加积分、开会员和调用物流等网关调用继续同步等待结果，以便 Worker 立即记录 `SUCCEEDED`、`RETRY_WAITING`、`RECONCILING` 或 `QUARANTINED`。

本阶段不修改履约成功后的权益或物流投影，不修改具体网关协议，也不删除现有履约状态机。

## 方案选择

考虑过三种实现方式：

1. 保留定时任务全量领取到期任务。改动最小，但正常任务仍依赖轮询延迟，无法利用 RabbitMQ 主动触发。
2. 创建任务后在事务提交回调中直接发布 RabbitMQ。延迟低，但进程可能在事务提交后、消息发送前退出，造成任务漏发。
3. 新增独立 `fulfillment_outbox`，创建或人工恢复任务时与任务状态变更同事务写入，独立 Relay 通过 Publisher Confirm 发布任务就绪事件；定时任务只补偿漏发、到期重试和租约恢复。这是本阶段采用的方案。

不直接复用现有 `message_outbox`。该表及其 Relay 当前只理解抽奖事件信封和 `lottery.events.topic`，强行复用需要把抽奖消息基础设施泛化为多领域总线，会扩大本轮范围并增加回归风险。履约 Outbox 独立建表，但沿用现有 Outbox 的租约领取、条件更新、Publisher Confirm 和指数退避模式。

## 数据模型

新增 `fulfillment_outbox` 表，字段包括：

- `id`：自增主键。
- `event_id`：全局唯一事件标识，唯一索引用于防止同一触发记录重复创建。
- `fulfillment_no`：关联的履约业务编号。
- `event_type`：固定为 `FULFILLMENT_TASK_READY`，为版本演进保留明确契约。
- `payload`：任务就绪事件 JSON 快照。
- `status`：`PENDING`、`PROCESSING`、`SENT` 或 `FAILED`。
- `retry_count`、`last_error`、`next_retry_at`：Relay 失败退避信息。
- `claim_token`、`claim_until`：多实例 Relay 的发送租约。
- `created_at`、`sent_at`：创建和确认发送时间。

`fulfillment_task`、`fulfillment_attempt` 和 `fulfillment_quarantine` 的表结构与语义保持不变。Outbox 只保证“任务就绪通知最终可发布”，不复制任务状态，也不替代履约审计表。

数据库迁移使用下一个 Flyway 版本 `V28__add_fulfillment_outbox.sql`。

## 事件契约与 RabbitMQ 拓扑

任务就绪事件为：

```java
FulfillmentTaskReadyEvent(
    String eventId,
    String fulfillmentNo,
    LocalDateTime occurredAt
)
```

- `eventId` 标识一次触发请求，用于发布确认、日志和排障。
- `fulfillmentNo` 是消费者定位任务的稳定业务键。
- `occurredAt` 是触发记录创建时间。

事件不携带履约类型、用户、载荷或重试次数。消费者必须重新读取 `fulfillment_task`，避免消息快照与数据库状态分叉。

RabbitMQ 使用以下持久化拓扑：

- 交换机：`fulfillment.direct`，durable direct exchange。
- 路由键：`fulfillment.task.ready`。
- 执行队列：`marketing.fulfillment.execute.queue`。
- 死信交换机：`fulfillment.dlx`。
- 死信队列：`marketing.fulfillment.execute.dlq`。

执行队列通过路由键绑定到 `fulfillment.direct`，并配置死信交换机。发布端启用 mandatory、Publisher Return 和 correlated Publisher Confirm；只有消息可路由且 Broker ACK 后，Relay 才把 Outbox 标记为 `SENT`。

## 创建与人工操作事务

### 创建任务

`FulfillmentTaskService.create` 增加本地事务边界。在同一个事务中：

1. 校验命令并生成请求快照和指纹。
2. 新建 `PENDING` 状态的 `fulfillment_task`。
3. 写入一条 `FULFILLMENT_TASK_READY` Outbox。
4. 提交后同步返回任务视图。

相同 `fulfillmentNo`、相同指纹的幂等重放直接返回已有任务，不额外创建 Outbox。相同编号但不同指纹仍返回幂等冲突。并发重复创建依靠任务唯一键收敛为一条任务和一条首次触发 Outbox。

### 人工重试与终止

人工重试保持同步接口。在同一个事务中将 `QUARANTINED` 任务恢复为 `PENDING`、处理隔离记录，并追加一条新的任务就绪 Outbox；接口不等待 RabbitMQ 发布或履约完成，仍返回更新后的任务视图。

人工终止只更新任务与隔离记录，不创建 Outbox。已经在队列中的旧消息到达后看到 `TERMINATED` 终态，直接安全确认而不调用网关。

## Relay 发布流程

独立履约 Relay 按批次查询以下记录：

- `PENDING`；
- 已到 `next_retry_at` 的 `FAILED`；
- `claim_until` 已过期的 `PROCESSING`。

每行通过带原状态条件的更新获取唯一 `claim_token` 和发送租约。Relay 反序列化事件并发布到 `fulfillment.direct`：

- Publisher Confirm ACK 且没有 Return：条件更新为 `SENT`。
- NACK、Return、Confirm 超时或发送异常：条件更新为 `FAILED`，记录截断后的安全错误，并按上限 5 分钟指数退避。
- 进程在领取后退出：租约到期后其他实例可重新领取。
- 进程在 Broker ACK 后、标记 `SENT` 前退出：允许重复发布，由消费侧的任务状态和领取条件保证幂等。

Relay 继续由轻量定时器驱动；它轮询的是小型 Outbox 投递表，不是扫描履约业务状态机。

## 指定任务消费与状态机

`FulfillmentWorker` 增加按 `fulfillmentNo` 执行单个任务的入口。`FulfillmentStateService` 增加按编号原子领取的方法，仍使用现有租约和 `FOR UPDATE`/条件更新语义：

- 只有 `PENDING` 且已到执行时间的任务能被 RabbitMQ 正常触发领取。
- `PROCESSING`、`SUCCEEDED`、`QUARANTINED`、`TERMINATED` 或尚未到期的任务返回“无需执行”，监听器正常确认消息。
- 领取成功后，数据库事务结束，再同步调用对应网关；网关调用期间不持有数据库事务。
- 网关结果继续交给现有 `recordResult`，写入尝试记录并转换为 `SUCCEEDED`、`RETRY_WAITING`、`RECONCILING` 或 `QUARANTINED`。
- 物流任务继续调用现有物流状态投影；除此之外不调整任何履约成功后的权益投影。

同一任务的重复消息或多个消费者并发到达时，只有一个消费者能获取任务租约，其余消息安全确认。业务网关返回的可重试、永久失败、未知结果等都是已经落库的业务结果，监听器应正常 ACK，不能依靠 RabbitMQ 重试替代数据库状态机。

只有事件无法解析、数据库暂时不可用或监听器在任务结果落库前出现未被 Worker 处理的基础设施异常时，监听器才抛出异常，使用现有容器的有限重试策略；重试耗尽后进入履约 DLQ。

## 补偿扫描

现有 `FulfillmentScheduler` 改为补偿角色，每轮按以下顺序执行：

1. 恢复租约已经过期的 `PROCESSING` 任务为 `RECONCILING`。
2. 批量领取并执行已到期的 `RETRY_WAITING` 和 `RECONCILING` 任务。
3. 批量领取仍为 `PENDING` 且超过补偿宽限时间的任务，覆盖 Outbox 未发布、消息丢失或消费者长期不可用等场景。

正常新建的 `PENDING` 任务在宽限时间内只由 RabbitMQ 消费者触发，避免消息与定时器立即竞争。补偿扫描继续复用现有批量 Worker 和租约逻辑，但不再承担正常任务的主要触发。

补偿宽限时间作为 `luckyhub.fulfillment.compensation-grace` 配置，默认 `30s`；现有 `poll-interval`、`batch-size`、`recovery-batch-size`、退避和租约配置继续有效。

## 配置与兼容边界

- RabbitMQ 继续使用营销服务现有连接和虚拟主机 `/dongqh`。
- 使用现有 Spring Boot 4 / Spring AMQP 4 的 `JacksonJsonMessageConverter`。
- 履约消息组件受 `luckyhub.messaging.enabled` 控制，与现有抽奖消息组件保持一致。
- 管理端 API 路径、请求体、响应体和权限码不变。
- 现有履约任务与隔离数据无需迁移或回填 Outbox；上线前已经存在的到期任务由补偿扫描接管。
- 不新增跨服务分布式事务，也不把配置迁入 Nacos。

## 测试与验收

按 TDD 分阶段验证：

1. Flyway 迁移创建完整的 `fulfillment_outbox` 约束和索引。
2. 创建新任务时任务与 Outbox 同事务成功；事务回滚时两者都不存在。
3. 幂等重放和并发重复创建只保留首次触发 Outbox；幂等冲突行为不变。
4. 人工重试同步恢复任务并追加新 Outbox；人工终止不追加 Outbox。
5. Relay 只有在 Confirm ACK 且未 Return 后标记 `SENT`，失败后可退避重试，过期租约可恢复。
6. RabbitMQ 拓扑包含 `fulfillment.direct`、执行队列、DLX 和 DLQ，并使用指定路由键。
7. 指定任务消费者只执行消息对应的到期 `PENDING` 任务；重复消息、终态任务和并发消息不会重复调用网关。
8. 网关成功、可重试失败、永久失败和未知结果仍由现有数据库状态机记录；消息监听器不把业务失败转成 Rabbit 重试。
9. 补偿扫描不会抢占宽限期内的新任务，但能处理漏发 `PENDING`、到期重试、对账任务和过期租约。
10. 管理端创建、查询、重试、终止接口契约以及现有履约恢复、隔离、安全测试继续通过。
11. 运行营销服务相关单元测试、完整测试和 Maven 编译；在本地 RabbitMQ `/dongqh` 验证声明、路由、重复投递与死信路径。

## 完成边界

本阶段完成后报告所有新增或修改文件、数据库迁移、交换机、队列、路由键、Outbox 重试、消费幂等、补偿扫描行为和测试结果。未经用户确认，不继续迁移履约成功后的权益投影或其他定时任务。
