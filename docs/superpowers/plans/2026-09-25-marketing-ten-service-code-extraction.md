# LuckyHub 十业务服务代码拆分实施计划

**Goal:** 将当前 Marketing 中的业务域提取为五个独立服务模块，与现有 item/cart/user/trade/pay 组成十个业务服务，同时保持外部契约和当前线上单一写入者。

**Architecture:** 新建 `lottery-service`、`points-service`、`coupon-service`、`membership-service`、`fulfillment-service` 五个同级可部署模块；跨服务依赖经 `hm-api` 契约，不共享业务 Mapper/Entity。提取期间旧 Marketing 保持当前写入者，直到对应审计、数据回填、服务验收和路由切换条件全部通过。

**Tech Stack:** Java 17、Spring Boot 4.0.8、Maven reactor、MySQL/Flyway、Nacos、RabbitMQ、Redis/Redisson、Seata AT、hm-api、hm-security。

## 全局约束

- 现有服务保持 `item-service`、`cart-service`、`user-service`、`trade-service`、`pay-service`；共享 `hm-security` 不计作业务服务。
- Marketing V1–V28 不改写；新 schema 使用新增迁移，数据库不由本代码任务执行或初始化。
- 身份/RBAC 数据移动、Gateway/Nginx 路由切换、账号/业务数据修改等待只读审计与逐域切换验收；旧 Marketing 在此之前保持唯一写入者。
- 抽奖 Redis Lua、配额键、Redisson 锁、TTL 与对账行为原样保留。
- 保留用户 ID/业务号稳定性、API JSON/错误语义、事件幂等、Outbox、库存条件更新、不可变积分流水和冲正行为。
- 不新增、修改或删除交接文档；不清理或覆盖现有工作区改动；不启动 Compose、容器或应用服务。

## 执行顺序

1. **五个服务模块骨架**：根 reactor 注册五个模块；每个模块先有独立 Maven artifact、受限包扫描的 Spring Boot 启动类和服务名；不添加业务实现或写入端点。用静态合同测试验证模块清单、父 POM、启动类包名和不存在对 `marketing-service` 的依赖。
2. **Coupon 提取**：迁移 `marketing-service/src/main/java/com/dongqh/luckyhub/coupon/**` 中的券模板、用户券、发券、结算预占/核销/释放、到期处理和支付成功事件消费。把目录、会员与用户授权调用改为真实 `hm-api` 端口/客户端；核对 `CouponConcurrencyTests`、Trade/Pay 的订单域幂等键与消息重放语义。旧 Marketing 兼容实现先保留但不得与新服务并行写入。
3. **Points 提取**：迁移 `points/**`，用 item-service 提供的商品/SKU/渠道库存契约及 fulfillment-service 实物订单契约替换同库 Mapper 调用；保留余额+不可变流水、幂等业务键、冲正与已确认的兑换状态行为。跨服务事务/补偿设计未验收前不路由流量。
4. **Membership 提取**：迁移 `membership/**`，定义支付成功和 Lottery 奖励的分域发放入口，保留折扣读取和订单快照语义；依赖身份、支付验真、停用并发及奖励产品/数量门槛，未决前不允许用户数据/路由切换。
5. **Lottery 提取**：将 activity/lottery/prize/drawchance/benefit/reward 及其 Outbox 与对账所有权归入 `lottery-service`；只能经契约调用资产/履约服务。全量保留 Redis Lua、键、锁、TTL、条件扣减和在途请求对账合同。
6. **Fulfillment 提取**：迁移 `fulfillment/**` 及 `shipping/**` 中非用户地址簿能力，按设计依赖 user/item/资产服务；用户地址所有权保持 `user-service`，抽奖权益状态由 Lottery 事件投影更新。
7. **切换与最终清理（受门槛控制）**：逐域完成只读数据库审计、数据迁移核对、接口/消息/并发/回滚验收和授权后，再改 Gateway/Nginx 路由、停用旧写入实现，最终移除 `marketing-service`。在门槛满足前只完成构建级代码提取，不启用新写入者。

## 当前执行批次

- 从 Task 1 开始，在当前含用户未提交内容的工作区做窄范围追加；不创建 worktree（它不会携带现有未提交文件），不切换分支、不暂存、不提交。
- Task 1 通过后继续各业务域代码提取；本计划不被视为数据库审计结果或生产切换批准。
