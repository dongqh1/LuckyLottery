# 支付成功可靠 RabbitMQ 事件设计

## 目标与范围

本阶段只升级支付成功链路：支付服务可靠发布一次支付成功事实，交易服务更新订单状态，营销服务异步核销订单使用的优惠券。支付申请、订单金额校验、余额扣减、支付单状态更新等需要立即返回结果的操作继续同步执行。

本阶段不处理履约任务、履约投影、订单超时和优惠券锁定超时等后续迁移项。

## 方案选择

考虑过三种方式：

1. 保留当前事务内直接 `RabbitTemplate.convertAndSend`。改动最小，但无法原子地保证支付单提交与消息发布，不能接受。
2. 在事务提交后发送 RabbitMQ 消息。可以避免事务回滚时提前消费，但进程在提交后、发送前崩溃仍会丢消息。
3. 支付数据库事务同时写入 Outbox，由 Relay 通过 Publisher Confirm 投递 RabbitMQ。消费者各自使用独立队列、幂等处理和死信队列。这是本阶段采用的方案。

## 事件契约

共享事件仍放在 `hm-api`，将 `PaySuccessMessage` 扩展为：

```java
PaySuccessMessage(
    String eventId,
    Long orderId,
    Long userId,
    Integer amount,
    Long couponId,
    LocalDateTime paidAt
)
```

- `eventId`：全局唯一消息标识，用于 Outbox、消费幂等和排障。
- `orderId`、`userId`、`amount`：交易订单更新所需快照。
- `couponId`：可空；没有使用优惠券的订单不触发核销。
- `paidAt`：支付服务确定的支付时间，避免消费者分别生成不同时间。

事件是支付完成后的事实，不允许消费者反向修改支付结果。

## RabbitMQ 拓扑

保留现有交换机和路由键以兼容当前链路：

- 交换机：`pay.direct`，durable direct exchange。
- 路由键：`pay.success`。
- 交易队列：`trade.pay.success.queue`。
- 营销队列：`marketing.pay.coupon-use.queue`。
- 死信交换机：`pay.dlx`。
- 交易死信队列：`trade.pay.success.dlq`。
- 营销死信队列：`marketing.pay.coupon-use.dlq`。

同一条支付事件分别进入两个业务队列。交易服务与营销服务独立确认、独立重试，一个消费者失败不会阻塞另一个消费者。

## 支付服务可靠发布

新增 `pay_message_outbox` 表。支付服务在同一个本地事务中完成：

1. 锁定并校验支付单和业务订单快照。
2. 扣减余额。
3. 将支付单更新为成功。
4. 写入 `PAY_SUCCESS` Outbox 记录。

事务中不再直接发送 RabbitMQ，也不再同步调用 `couponCheckoutClient.use`。

Outbox Relay 定时租约领取待发送记录，使用关联数据和 Publisher Confirm 向 `pay.direct` 发布。Broker 未确认或发送异常时记录失败原因并指数退避重试；确认成功后将 Outbox 标记为 `SENT`。多实例通过租约与条件更新避免同时处理同一行。

## 消费幂等与错误处理

### 交易服务

`markOrderPaySuccess` 改为幂等状态转换：

- 待付款且用户、金额匹配：更新为已支付并写入事件中的 `paidAt`。
- 已支付且用户、金额匹配：视为重复消息，直接成功。
- 订单不存在、用户不匹配、金额不匹配或处于不允许状态：抛出业务异常，消息按容器策略进入死信链路。

### 营销服务

只有 `couponId` 非空时才核销优惠券。核销必须同时校验优惠券、用户和锁定订单号：

- `LOCKED` 且锁定订单一致：更新为 `USED`。
- 已经由同一订单核销：视为重复消息，直接成功。
- 优惠券不存在、所属用户不匹配、锁定订单不匹配或状态冲突：抛出业务异常并进入死信链路。

营销消费者不通过 HTTP 回查交易服务，完全使用事件快照。

## 配置与兼容边界

- RabbitMQ 继续使用现有 `.env` 中的账号、密码和虚拟主机 `/dongqh`。
- 不把配置迁入 Nacos。
- 使用 Spring Boot 4 / Spring AMQP 4 的 `JacksonJsonMessageConverter`。
- 旧回滚单体 `mall-backend/hm-service` 不接入本次消费者，防止和当前微服务重复处理。
- 现有支付成功 HTTP 接口暂时保留用于兼容，但支付服务不再调用它。

## 测试与验收

按 TDD 分阶段验证：

1. 事件契约包含 `eventId`、`couponId` 和 `paidAt`。
2. 支付事务写 Outbox，不直接调用 RabbitMQ 或优惠券 HTTP 客户端。
3. Outbox Relay 仅在 Broker Confirm 成功后标记 `SENT`，失败时可重试。
4. 交易消费者第一次更新成功、重复消费成功、金额或用户冲突失败。
5. 营销消费者正确核销、无优惠券时跳过、重复消费成功、锁定关系冲突失败。
6. RabbitMQ 拓扑包含两个业务队列、DLX 和两个 DLQ。
7. 分别运行 `hm-api`、`pay-service`、`trade-service`、`marketing-service` 的相关单元测试和编译。
8. 在本地 RabbitMQ `/dongqh` 上验证交换机、绑定、消息路由与死信路径，并清理测试消息。

## 完成边界

本阶段完成后必须暂停并报告所有文件、数据库迁移、交换机、队列、路由键、重试/死信机制以及测试结果。未经用户确认，不开始下一项履约任务迁移。
