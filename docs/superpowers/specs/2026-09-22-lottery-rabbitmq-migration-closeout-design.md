# 抽奖 RabbitMQ 迁移收尾设计

## 目标

完成抽奖异步消息链路的迁移收尾，使消息总开关能够完整控制 RabbitMQ 组件，并在真实 MySQL、Redis、RabbitMQ 环境中验证 Outbox 发布与消费闭环。保留现有数据库 Outbox、抽奖状态、Redis 配额、分布式锁和补偿扫描机制。

## 范围

- 修正 `luckyhub.messaging.enabled` 的装配边界。
- 验证开关关闭时不创建抽奖 RabbitMQ 消息转换器、拓扑、发布器、监听器、Outbox Relay 及其调度器。
- 验证开关开启时上述组件保持可用。
- 使用现有 `compose.yaml` 启动 MySQL、Redis 和 RabbitMQ。
- 执行真实 MySQL Outbox 持久化测试。
- 执行真实 RabbitMQ 拓扑、发布、路由、消费和幂等落库验证。
- 整理本地变更及最终提交建议，但不执行 Git 提交。

## 不在本轮范围

- 不移除数据库 Outbox。
- 不移除或替换 Redis 配额、Lua、分布式锁和过期预约恢复。
- 不改变抽奖业务接口或管理端同步行为。
- 不引入 Testcontainers 或新的基础设施依赖。
- 不顺带重构权益投影或其他消息域。

## 设计

### 消息开关

`LotteryMessagingConfiguration` 始终注册 `MessagingProperties`，因为消息关闭时数据库 Outbox、奖励履约等非 Rabbit 组件仍依赖这些通用可靠性参数。仅 RabbitMQ 消息转换器和抽奖拓扑声明使用 `luckyhub.messaging.enabled=true` 条件（缺省视为 true）。

发布器、监听器、Outbox Relay 和 Relay 调度器继续使用相同条件。这样关闭开关时不会声明交换机、队列或启动抽奖消息监听；开启时维持现有行为。

### 测试策略

采用测试驱动方式：

1. 新增轻量 Spring 上下文测试，先证明当前实现关闭开关时仍错误创建 RabbitMQ 基础设施 Bean。
2. 添加 Bean 级配置条件后，使关闭和开启两组断言通过，并确认关闭时 `MessagingProperties` 仍可注入。
3. 保留现有拓扑结构测试，继续验证持久化 Topic Exchange、业务队列、DLX、DLQ 和三条业务路由。
4. 运行发布器、监听器和 Outbox Relay 单元测试。
5. 在真实基础设施上运行 MySQL Outbox 集成测试，并增加或运行 RabbitMQ 真链路集成测试，验证消息被路由和消费，重复消息不会重复产生业务副作用。

### 联调环境

复用根目录 `compose.yaml` 中的 MySQL、Redis、RabbitMQ 服务。密码和端口从本地 `.env` 或当前环境读取，不输出敏感值，不写入源码。依赖服务必须通过健康检查后再运行测试。

如果 Docker 或必要环境变量不可用，应报告具体阻塞点；不得用模拟测试冒充真实联调成功。

### 完成标准

- 消息关闭时抽奖 RabbitMQ 基础设施 Bean 不装配，但 `MessagingProperties` 仍可供数据库与履约组件注入；开启时 RabbitMQ 组件正常装配。
- RabbitMQ 与 Outbox 相关单元测试全部通过。
- 真实 MySQL Outbox 事务提交、回滚及唯一约束测试通过。
- 真实 RabbitMQ 能声明目标拓扑，发布消息获得确认并由目标消费者处理。
- 工作区中不存在活动的 Redis Stream 抽奖消息实现。
- 提供完整验证命令、结果和未提交文件清单，不执行 Git commit。

## 风险与处理

- 本地端口冲突：优先使用 Compose 已配置的端口覆盖，不修改业务默认值。
- 历史数据干扰：集成测试使用唯一事件 ID，并依靠事务或显式清理测试数据。
- 消费异步时序：使用有界条件等待，不使用不确定的长时间固定休眠。
- 环境服务不可用：区分代码失败与基础设施失败，保留可复现的诊断证据。
