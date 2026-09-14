# Seata AT 微服务客户端集成设计

## 目标

在现有 Seata TC、Nacos 和独立业务数据库基础上，为 `item-service`、`cart-service`、`user-service`、`trade-service`、`pay-service` 与 `marketing-service` 接入 Seata AT 模式，使订单创建、补选优惠券、取消订单和余额支付等同步跨库写入能够在失败时整体回滚。

本次只保护关系数据库分支。Redis、Redis Stream、Outbox、定时任务及外部系统调用不属于 AT 数据源代理能力范围，继续使用现有幂等、可靠消息和补偿机制。

## 版本与依赖

项目使用 Spring Boot 4.0.8、Spring Cloud 2025.1.0 和 Spring Cloud Alibaba 2025.1.0.0。六个服务统一引入 BOM 管理的 `spring-cloud-starter-alibaba-seata`，并在根 Maven 依赖管理中显式固定 Seata 客户端版本，使所有服务解析到同一版本。

Spring Cloud Alibaba 2025.1.0.0 BOM 管理的 Seata 客户端版本为 2.5.0，而当前 TC 为 2.4.0。Apache Seata 的升级指南要求服务端与客户端版本一致，因此本次把 TC 和六个客户端统一升级/固定到 2.5.0，不长期混用不同次版本。Seata 2.5 将 HTTP/控制台端口合并到事务端口 8091，现有 TC 配置、Docker 端口映射、健康检查和相关契约测试同步调整。

网关和 `hm-api` 不持有业务数据源，不引入 Seata 客户端。

## 客户端配置

六个服务使用相同事务组 `hmall_tx_group`，并映射到 TC 的 `default` 集群。每个服务配置：

- `seata.enabled=true`，允许测试或故障隔离时通过环境变量关闭。
- `seata.application-id=${spring.application.name}`。
- `seata.tx-service-group=hmall_tx_group`。
- 数据源代理模式为 `AT`，启用自动数据源代理。
- 注册中心类型为 Nacos，地址、用户名和密码复用现有 `NACOS_*` 环境变量。
- 注册中心服务名为 `seata-server`，分组为 `DEFAULT_GROUP`，集群为 `default`。
- `seata.service.vgroup-mapping.hmall_tx_group=default`。

Docker Compose 为六个服务提供一致的 Nacos/Seata 开关变量，并在启动依赖中等待 `seata-server` 健康。开发机默认连接宿主机 Nacos，容器环境连接 `nacos:8848`。

## AT 回滚表

每个参与 AT 的物理业务库必须有自己的 `undo_log`：

- `hm_item`
- `hm_cart`
- `hm_user`
- `hm_trade`
- `hm_pay`
- `luckyhub`

前五个库在现有可重复执行的数据库初始化迁移中使用 `CREATE TABLE IF NOT EXISTS` 创建标准 Seata 2.5 `undo_log`。`luckyhub` 由 Flyway 管理，新增顺序版本迁移。表包含 `(xid, branch_id)` 唯一键和 `log_created` 清理索引，存储引擎为 InnoDB、字符集为 utf8mb4。

## XID 传播

OpenFeign 调用继续由 Spring Cloud Alibaba Seata 集成自动附带和接收 XID。

现有营销集成同时使用 `RestTemplate` 和 `RestClient`，因此补充统一的客户端请求拦截器：仅当 `RootContext` 中存在 XID 时写入 Seata 标准请求头，普通请求不产生空头。服务端由 Seata Starter 的 Web MVC 拦截器绑定和清理入站 XID。

拦截器通过 Boot 的 `RestTemplateCustomizer`/`RestClientCustomizer` 注入，避免在每个业务客户端中重复拼接事务头。需要覆盖 `trade-service`、`pay-service`、`user-service` 和 `marketing-service` 中的普通 HTTP 出站调用。

## 全局事务边界

只在同步跨库写入口创建全局事务：

- `trade-service.createOrder`：订单、物流、明细、购物车、库存及优惠券预占。
- `trade-service.applyCheckoutCoupon`：交易金额、优惠券预占及支付单金额同步。
- `trade-service.cancelOrder`：订单取消及优惠券释放。
- `pay-service.tryPayOrderByBalance`：支付单、用户余额、订单状态及优惠券核销。

这些入口使用 `@GlobalTransactional`，保留本地 `@Transactional`，确保入口数据库操作同时是本地事务和 AT 分支。纯查询或单库写入不创建全局事务。

营销、商品、购物车、用户等被调服务通过入站 XID 自动加入当前全局事务，不在分支方法上再次开启新全局事务。

## 补偿与异常规则

现有优惠券释放逻辑只在没有活动 XID 时作为传统补偿执行。处于 Seata 全局事务时，优惠券数据库更新由 AT 回滚，禁止再发一次释放请求，避免重复补偿和全局锁冲突。

所有跨服务写失败必须继续向上抛出运行时异常，不能由 Feign fallback 或 HTTP 客户端转换为成功结果。全局事务超时或 TC 不可用时请求失败，不允许静默降级成部分提交。

AT 不回滚 Redis、消息或外部物流动作；这些路径保留原有幂等键、Outbox 和对账恢复机制，不把长时间异步流程包进全局事务。

## 测试与验收

实施遵循测试先行：

1. 新增契约测试，先证明六个依赖、六套配置、六张 `undo_log`、Compose 依赖和四个事务入口尚未完整接入。
2. 新增 Java 单元测试，绑定测试 XID 后验证 `RestTemplate` 与 `RestClient` 请求携带事务头；解绑后验证不携带。
3. 修改实现后运行契约测试、相关模块单测和六模块 Maven 编译。
4. 检查 Maven 依赖树，确保只有一个 Seata 客户端版本，并与 TC 版本一致。
5. 在 Docker 环境可用时启动 TC 和六服务，制造库存或支付链路中途失败，查询六个业务库确认已写数据回滚且 `undo_log` 最终清理。

验收失败时报告具体阻塞，不以“配置已添加”代替可编译、可连接和可回滚的验证。

## 交付说明

最终交付按文件逐项列出：文件路径、具体修改、修改原因，并附上实际运行的测试/构建结果以及未能执行的环境级验证。
