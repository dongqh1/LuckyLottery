# Marketing 微服务拆分设计

## 1. 目标与范围

将当前 `marketing-service` 拆成职责单一、可独立部署的同级微服务。属于商城既有用户、商品、订单、支付职责的能力并入相应服务；其余营销业务拆分为 `lottery-service`、`points-service`、`coupon-service`、`membership-service` 和 `fulfillment-service`。

迁移按业务域分阶段完成。每个域完成代码迁移、数据迁移、API 路由切换和验收后，删除旧服务中的对应实现；迁移期间由 Gateway 将尚未切换的路径转发到旧服务。任何时刻只允许一个服务拥有某项业务写入逻辑。

本设计承接 `docs/superpowers/specs/2026-09-09-luckyhub-microservice-extraction-design.md` 中的可靠性要求，并更新其服务边界：积分、优惠券和会员从一个 `marketing-service` 再细分成独立服务，商城 API 经 `hm-gateway` 统一转发。旧文档中仍适用的抽奖、Outbox、履约、认证和数据所有权约束继续保留；与本设计边界冲突的包归属以本文件为准。

## 2. 当前结构依据

- 根 Maven 聚合工程使用 Java 17、Spring Boot 4.0.8；现有 `item-service`、`cart-service`、`user-service`、`trade-service`、`pay-service`、`hm-api` 和 `hm-gateway` 均为同级模块。
- `marketing-service` 目前包含 activity、auth、benefit、catalog、coupon、drawchance、fulfillment、integration、inventory、lottery、membership、order、payment、points、prize、rbac、reward、shipping 等业务域。
- 现有 `hm-api` 已集中定义 Feign Client、DTO 和支付成功消息；`hm-gateway` 已使用 Nacos 服务发现并验证 RS256 JWT、清除外部传入的 `user-info` 后写入可信用户 ID。
- Nginx 当前保留浏览器 `/api/marketing/**` 路径，将其改写成后端 `/api/**`，但直接访问旧 `marketing-service`。本设计改为转发到 Gateway。
- 营销 V8 已建立 `product`、`product_sku`，V9 建立渠道库存，V18/V19/V20 将它们关联到黑马商品。营销 V13 建立现金订单和支付表；V17/V28 增加物流履约及 Outbox；V1/V16 建立抽奖记录、权益及抽奖机会数据。

## 3. 目标模块结构

```text
hmall-lottery/
├─ hm-api/                    # Feign 接口、跨服务 DTO、事件契约
├─ hm-security/               # 共用的用户上下文与权限拦截库，不单独部署
├─ hm-gateway/                # 商城与营销 API 统一入口、认证和服务路由
├─ item-service/              # 商城商品、SKU、渠道库存
├─ cart-service/              # 购物车
├─ user-service/              # 用户、认证、角色和权限
├─ trade-service/             # 商城与营销现金订单
├─ pay-service/               # 商城与营销支付单、支付回调
├─ lottery-service/           # 活动、抽奖、奖品、抽奖机会和中奖权益
├─ points-service/            # 积分账户、流水、积分兑换
├─ coupon-service/             # 优惠券模板、用户券、预占和核销
├─ membership-service/         # 会员商品、会员账户和会员权益
└─ fulfillment-service/        # 奖励发放、实物领取、发货和物流
```

`hm-security` 是共享 Java 库，不是服务进程。它承载从营销代码中提取的可信用户上下文、`@RequirePermission` 注解和统一权限拦截；权限数据、角色管理与权限缓存由 `user-service` 唯一管理。这样多个服务复用一份执行逻辑，而不复制 RBAC 数据或拦截器实现。

## 4. 业务归属

| 目标模块 | 迁入能力 | 边界说明 |
|---|---|---|
| `item-service` | `catalog`、`inventory` | 统一商品/SKU 主数据与渠道库存。将营销商品映射到现有商城商品，复用稳定商品 ID；营销渠道库存表归商品服务。抽奖奖品的活动额度不属于商品库存。 |
| `user-service` | `auth`、`rbac`、`shipping` 中的用户地址簿 | 用户服务签发商城和营销共用的 RS256 JWT，保存角色、权限和用户授权，并作为唯一地址簿。营销账号映射到现有商城用户 ID，不再维护第二套客户账号或密码；地址 API 兼容路径 `/api/shipping/addresses/**` 也由用户服务处理。 |
| `trade-service` | `order` | 营销现金订单并入统一订单服务。建立与已有商城订单兼容的订单类型和明细，不新增第二套现金订单真相源。 |
| `pay-service` | `payment` | 支付单、支付回调与支付状态并入统一支付服务；支付成功仍由 Outbox 产生可靠事件。 |
| `hm-security`（共享库） | `auth`、`rbac` 中的可信请求上下文和权限拦截 | 只保留一份各服务共同使用的实现。权限数据和账号认证归 `user-service`；服务独有配置留在服务自身。 |
| `lottery-service` | `activity`、`lottery`、`prize`、`drawchance`、`benefit`、`reward` | 活动发布、奖池、抽奖、机会账户、中奖权益和权益状态投影在一个服务内管理。保存奖励定义快照；权益发放由 `fulfillment-service` 编排并调用 `points-service`、`coupon-service` 或 `membership-service`。抽奖剩余奖品量仍使用 MySQL 条件更新作为最终事实。 |
| `points-service` | `points` | 管理积分账户、不可变积分流水、运营调整、冲正和积分兑换。商品实物信息及真实库存由 `item-service` 提供。 |
| `coupon-service` | `coupon` | 管理券模板、用户券生命周期，以及购物车结算中的试算、预占、核销和释放。优惠券适用范围使用商品服务的稳定商品 ID/分类/品牌信息。 |
| `membership-service` | `membership` | 管理会员商品、会员账户、开通、有效期、折扣查询和会员发放记录。会员购买现金订单由 `trade-service` 创建、由 `pay-service` 收款。 |
| `fulfillment-service` | `fulfillment`、`shipping` 中除用户地址簿外的部分 | 管理奖励发放任务、实物领取与物流状态、从用户服务读取后加密保存的收货地址快照、物流单、轨迹、回调、重试、隔离和对账。履约状态通过事件回传给抽奖服务更新权益状态投影；履约服务不直接写 `user_benefit`。模拟器仅供 dev/test 使用，不作为生产适配器。 |
| 各调用服务及 `hm-api` | `integration` | 原 `RestClient`/Gateway 调用改为 `hm-api` 契约及调用方 Adapter；物流和履约外部适配器归 `fulfillment-service`。模拟器仅供 dev/test 使用，不作为生产适配器。 |
| 各服务自身 | `common`、`config` 中的服务专属部分 | Controller 响应、异常和服务配置按实际所有者迁移；只把认证上下文/权限拦截抽到 `hm-security`，不建一个包含全部业务基础设施的臃肿 common 模块。 |

各服务使用独立数据库，不跨服务直接读写表或建立跨库外键。营销表的业务号、用户 ID、商品 ID、订单 ID 等外部引用保留为普通字段，并通过接口/事件验证。营销 `product/product_sku` 迁移时须与商城 `item` 主数据合并映射，不能保留两套可编辑商品目录。营销 `user_shipping_address` 通过审计过的字段和密文转换并入 `hm_user.address`；为已存在的物流快照保留原地址 ID 到新地址 ID 的映射，不保留第二套可编辑地址簿。

## 5. hm-api 与 hm-security

### hm-api

`hm-api` 继续作为不启动的纯契约 JAR，只保存服务间 HTTP/Feign 契约、消息 DTO 和版本化事件类型，不保存 Controller、Service、Mapper、Entity、业务规则、权限拦截实现或某服务内部异常。

按服务域组织接口和事件，至少覆盖：

- `ItemClient`：商品/SKU 查询、渠道库存预占/确认/释放；
- `UserClient`：本人地址读取、用户/权限查询；
- `TradeClient`、`PayClient`：统一订单和支付协作；
- `LotteryClient`：抽奖机会的幂等发放；
- `PointsClient`、`CouponClient`、`MembershipClient`：各类资产的幂等发放及优惠券预占/确认/释放；
- `FulfillmentClient`：履约任务创建和状态查询；
- 事件 DTO：`OrderPaidEventV1`、`LotteryRewardCreatedEventV1`、`PointsRedemptionCreatedEventV1`、`FulfillmentStatusChangedEventV1`。

只将真实跨服务调用所需的契约加入 `hm-api`。服务提供方不反向依赖 `hm-api` 中的客户端实现；契约使用普通 Java 类型，不暴露 ORM Entity、Servlet 类型或业务实现。

### hm-security

抽出一次并复用当前营销权限保护机制：服务从 Gateway 传入的可信用户 ID 建立请求上下文；`hm-security` 定义统一的 `PermissionLookup` 接口和 `@RequirePermission` 拦截器。除 `user-service` 使用本地权限数据实现该接口外，其他业务服务经 `hm-api` 的 `UserClient` 查询 `user-service` 授权，避免用户服务调用自己。权限数据与 Redis 缓存在 `user-service` 控制。Gateway 只负责 JWT 验证与用户身份传递，业务服务仍负责本人数据范围和操作权限，不能把 Gateway 路由当作业务授权。

## 6. API 与 Gateway 路由

### 浏览器入口兼容

浏览器现有 `/api/marketing/**` URL 保持不变。Nginx 特殊 location 保留现有改写规则 `^/api/marketing/(.*)$ -> /api/$1`，但上游从 `marketing-service:8080` 改为 `hm-gateway:8080`。Gateway 将收到的 `/api/**` 路径原样转发给对应服务；各服务暂时保留现有 `/api/...` Controller 路径，避免同一阶段重写前端和业务接口。

商城现有 `/api/**` 请求继续由 Nginx 改写后进入 Gateway，现有 `/items/**`、`/carts/**`、`/users/**`、`/addresses/**`、`/orders/**`、`/pay-orders/**` 路由行为保持不变。外部传入的 `user-info` 由 Nginx 清除，Gateway 再清除一次并仅在 JWT 验证成功后写入用户 ID。

### Gateway 服务路由表

Gateway 增加显式、互不含糊的营销路径规则，目标服务通过 Nacos 服务名发现，路径和请求方法保持原样：

| 收到的路径 | Nacos 服务 |
|---|---|
| `/api/products/**`、`/api/admin/products/**`、`/api/admin/inventory/**` | `item-service` |
| `/api/auth/**`、`/api/admin/users/**`、`/api/admin/roles/**`、`/api/admin/permissions/**`、`/api/shipping/addresses/**` | `user-service` |
| `/api/lottery/**`、`/api/benefits/**`、`/api/admin/activities/**`、`/api/admin/prizes/**`、`/api/admin/prize-images/**`、`/api/admin/reward-definitions/**` | `lottery-service` |
| `/api/points/**`、`/api/admin/points/**` | `points-service` |
| `/api/coupons/**`、`/api/admin/coupon-templates/**`、`/api/admin/coupons/**` | `coupon-service` |
| `/api/memberships/**`、`/api/admin/membership-products/**` | `membership-service` |
| `/api/shipping/orders/**`、`/api/shipping/callbacks/**`、`/api/admin/shipping/**`、`/api/admin/fulfillment/**`、`/api/admin/simulators/**` | `fulfillment-service` |
| `/api/orders/**` | `trade-service` |
| `/api/payments/**`、`/api/admin/payments/**`、`/callbacks/payments/**` | `pay-service` |

为兼容当前 Nginx marketing rewrite 可能生成的 `/api/callbacks/payments/**`，Gateway 增加一条精确兼容规则并仅剥离开头的 `/api`，转发到服务原有 `/callbacks/payments/**`。Nginx 也为原生 `/callbacks/payments/**` 增加直达 Gateway 的入口，不额外改写路径。物流回调保留 `/api/shipping/callbacks/logistics`，在 Gateway 只豁免 `POST` 这一精确路径的 JWT 检查，继续要求履约服务执行现有签名、时间窗和去重校验。

`/api/users/login` 继续走商城现有 `user-service` 登录路由；营销兼容别名 `/api/auth/**` 也由同一 `user-service` 处理。Gateway 只将 `POST /users/login` 和 `POST /api/auth/login` 配成登录白名单；其他营销 API 仍要求 JWT。除当前物流回调的精确 `POST /api/shipping/callbacks/logistics` 例外外，回调认证行为遵循现有合同；任何支付回调免 JWT 的改动都需单独验证签名与防重放边界。

### 迁移期旧路由

尚未迁出的营销路径由 Gateway 的低优先级兼容路由代理到旧容器 `marketing-service:8080`。新增服务规则优先级高于兼容路由。某个业务域切换后，旧服务立即移除该域的 Controller、写入逻辑和 Mapper；对应兼容路由也删除。最终删除旧服务以及 Nginx/Gateway 的兼容路由，不留下并行写入或双份实现。

## 7. 数据与可靠消息

每个目标服务使用独立 schema：复用现有 `hm_item`、`hm_user`、`hm_trade`、`hm_pay`；新建 `hm_lottery`、`hm_points`、`hm_coupon`、`hm_membership`、`hm_fulfillment`。本设计不要求新建独立 MySQL 集群。

迁移表按以下所有权分配；实际 mapper/PO 与迁移 SQL 盘点若发现额外表，必须在实施计划中补入唯一所有者后再迁移：

| 所有者 | 主要现有表 |
|---|---|
| `user-service` | `sys_user`（仅账号映射，不复制商城客户）、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`user_shipping_address`（与现有用户地址簿合并） |
| `item-service` | `product`、`product_sku`、`sku_inventory`、`inventory_channel_stock`、`inventory_reservation`、`inventory_ledger` |
| `trade-service` | `mall_order`（按统一订单模型迁移其单 SKU 明细及价格快照） |
| `pay-service` | Marketing 的 `payment_order` 按业务号映射到现有支付单模型；支付事件 Outbox 和消费幂等记录继续归支付服务 |
| `lottery-service` | `marketing_activity`、`marketing_activity_prize`、`marketing_prize`、`lottery_draw_order`、`lottery_draw_record`、`user_benefit`、`draw_chance_account`、`draw_chance_ledger`、`draw_chance_reservation`、`reward_definition`、抽奖 Outbox/消费幂等/隔离记录 |
| `points-service` | `points_account`、`points_ledger`、`points_redemption_offer`、`points_redemption_order` |
| `coupon-service` | `coupon_template`、`user_coupon`、`coupon_issue_record` 及券消费幂等记录 |
| `membership-service` | `membership_product`、`user_membership`、`membership_grant_record` |
| `fulfillment-service` | `fulfillment_task`、`fulfillment_attempt`、`fulfillment_quarantine`、`fulfillment_outbox`、`shipping_address_snapshot`、`shipping_order`、`shipping_tracking_event`、`shipping_callback_receipt` |

`sim_*` 与 `sim_failure_rule` 仅作 dev/test 数据，不成为生产服务业务表；如果测试要求持久化，放在测试专用 schema 或测试资源中。

营销 V1 至 V28 已执行过的 Flyway 文件不得改写。每个服务建立自己的增量迁移和迁移/回填步骤，保留已有稳定 ID 和业务号；通过总数、金额、状态、库存余额、唯一键与抽样链路核对后切流。旧 `luckyhub` 在切流后改为只读观察，最终归档由实施阶段决定，不在服务启动时自动删除。

跨服务副作用使用明确的同步命令或版本化事件：

- `trade-service -> coupon-service`：结算试算和券预占；取消订单释放，支付成功核销。使用稳定订单号和券 ID 幂等处理；失败通过订单状态机/补偿恢复。
- `trade-service -> membership-service`：读取会员折扣；购买会员仍经过 trade/pay。
- `pay-service -> points-service`、`membership-service`：支付成功后按已验证业务规则发放积分或会员。事件由 Outbox 发布并使用事件 ID 和业务号幂等消费。
- `lottery-service -> fulfillment-service`：抽奖事务同时保存结果快照、中奖权益及 Outbox 记录；履约服务按奖励类型创建发放任务，通过各资产服务的幂等写接口发放积分、优惠券和会员。
- `lottery-service -> fulfillment-service`（实物领取）：权益服务先检查用户、状态和领取期限，再以 Seata AT 与履约服务共同提交幂等发货单/权益状态转换，保持现有同步领取结果；履约服务不直接改抽奖库。
- `fulfillment-service -> lottery-service`：奖励发放和物流状态变化发布 `FulfillmentStatusChangedEventV1`；抽奖服务幂等更新 `user_benefit` 状态投影。订单履约和权益记录之间只用稳定 `benefitNo`/业务号关联。
- `points-service -> fulfillment-service`：实物积分兑换完成扣分和营销额度预留后发事件，履约服务创建地址快照和物流任务。
- `fulfillment-service -> user-service/item-service`：读取本人收货地址和商品快照，不复制用户地址或商品主数据。

所有参与 Seata 的新服务继续使用现有 `hmall_tx_group`、Nacos 发现的 TC 和各自数据库 `undo_log`。逐条盘点现存 `@GlobalTransactional` 业务入口与本设计标出的跨库强一致流程：对仍需跨服务数据库原子回滚的流程，保留或补齐 Seata AT 客户端；只有经过业务不变量评审和故障补偿验证后才改为 saga。Redis、RabbitMQ、Outbox 和外部 HTTP 副作用不因 Seata 而变成原子操作，继续使用幂等、Outbox、状态机和补偿。

抽奖配额继续使用 Redis Lua 原子预留和确认；抽奖最终奖品数量继续使用 MySQL 条件扣减。Outbox 租约、Redis Stream Pending 重领、幂等消费、指数退避、隔离及对账补偿随原逻辑迁移。不得假设数据库本地事务可以覆盖多个服务或 Redis、RabbitMQ、HTTP 副作用。

## 8. 渐进实施顺序

1. **契约与统一入口**：扩展 `hm-api`，抽取 `hm-security`，新增 Gateway 服务发现路由和路由契约，Nginx 将营销路径送至 Gateway；未迁移功能使用旧服务兼容路由。
2. **身份、商品与库存归并**：将 RBAC 关联到现有 `user-service` 用户；把营销目录/SKU 映射到商城商品，迁移渠道库存至 `item-service`。保留抽奖奖品库存独立事实。
3. **订单与支付归并**：把营销现金订单并入 `trade-service`，支付记录和回调并入 `pay-service`；以 Outbox 事件替代 Marketing Base URL 中的点对点支付奖励调用。
4. **资产服务拆分**：依次迁移 `coupon-service`、`points-service`、`membership-service`，每个服务独立回填数据、切换 Gateway 路由并删除旧写入代码。
5. **抽奖服务抽取**：迁移活动、抽奖、奖品、抽奖机会和权益结果，验证并发、配额、幂等、条件扣减、Outbox 和对账后切换路径。
6. **履约服务抽取**：迁移奖励发放、地址快照、发货、物流回调和补偿；真实适配器在生产启用，模拟器只在 dev/test。
7. **最终清理**：删除全部旧业务实现、旧服务专属环境变量、兼容 Gateway 路由和 `marketing-service` Maven/Compose 模块；保留迁移审计记录和经核对的历史数据。

每个阶段单独验收和可回滚。新服务路径切换前需完成构建、现有契约测试、服务级集成验证、全新 schema 初始化、历史数据核对、对应前端/API 冒烟及回滚路由演练。不得在一次切换中同时改变外部 API 契约和底层业务归属。

## 9. 必须保留的行为

- RS256 JWT 用户 ID 与角色语义、请求身份头清洗和用户本人数据范围；
- 活动发布状态、抽奖请求幂等、单用户/活动并发锁、Redis Lua 配额和 MySQL 条件扣减；
- 积分不可变流水与反向冲正、优惠券状态 CAS、会员有效期和折扣规则；
- 支付成功 Outbox、库存预占/确认/释放、抽奖奖励快照、履约租约与补偿；
- 物流回调验签、时间窗、去重、合法状态迁移和敏感数据脱敏；
- 当前 Portal/Admin 页面所依赖的 JSON 字段、错误语义和 `/api/marketing/**` 公共 URL。

任何为满足新边界而需要调整的业务行为都必须在专门阶段设计迁移规则和兼容测试，不得顺手删除。

## 10. 不在本次范围

不新增真实第三方支付或物流厂商、不重做营销前端、不改变商城公共 URL、不新增 Kubernetes 或新的数据库集群，也不在迁移期间引入多处并行写入。服务拆分以保留现有功能、数据与并发可靠性为验收标准。
