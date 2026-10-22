# HMall Lottery 融合工程

本目录是黑马商城与 LuckyHub 营销核心的隔离融合工作区，原始四个来源目录保持不变。

## 当前目录

- `mall-backend/`：黑马商城 Java 后端原始副本（`hm-common` + `hm-service`）。
- `marketing-service/`：LuckyHub Java 17 后端源码快照，后续只保留并适配营销核心。
- `mysql/`：黑马商城原始 MySQL 配置和初始化数据。
- `nginx/`：黑马商城 Portal/Admin 静态前端和 Nginx 配置。
- `tests/`：融合工作区结构与集成契约测试。

## 本地运行与验证

本仓库统一使用 JDK 17、Spring Boot 4.0.8 和 Maven 3.9+。运行 `mvn -version` 时必须显示 Java 17；IDEA 的 Project SDK、Maven Runner JRE 和各 Spring Boot 运行配置也应选择 JDK 17。

在仓库根目录执行全仓编译：

```powershell
mvn -B -ntp clean test-compile -DskipTests
```

先根据 `.env.example` 准备被 Git 忽略的 `.env`，并为数据库、Redis、JWT、地址加密和物流管理员账号配置本地开发值。抽奖、积分、优惠券、会员及物流服务位于 `marketing-extraction` profile；默认启动命令不会启动这些服务。前后端完整联调请使用：

```powershell
docker compose --env-file .env --profile marketing-extraction up --build -d
```

该 profile 会启动商品、购物车、用户、交易、支付、抽奖、积分、优惠券、会员和物流服务，以及商城网关、Nacos、MySQL、Redis、RabbitMQ 和 Nginx。Nginx 将商城 API 转发到网关，由网关校验登录令牌并向业务服务传递用户身份。`LOGISTICS_ADMIN_USER_IDS` 可在 `.env` 中配置物流管理员用户 ID，默认是 `1`。

容器启动并健康后，通过 Portal/Admin 静态页、登录以及各服务边界执行冒烟验证：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Smoke-Test.ps1
```

如需单独启动旧单体进行回滚诊断，可启用 `rollback` profile：

```powershell
docker compose --env-file .env --profile rollback up --build -d mall-service
```

该回滚命令只启动 `mall-service`。Nginx 仍按默认配置把商城接口路由到网关和五个拆分服务；只有在明确恢复旧 Nginx 配置后，流量才会重新进入单体。

## Seata TC

默认 Docker 栈会初始化 `seata` 数据库，并启动基于
`apache/seata-server:2.5.0.jdk17` 构建的 TC。MySQL、Nacos、Seata 和各业务服务
统一通过 `hmall-lottery_default` 网络通信。当前 2.5 运行镜像以非 Web 模式启动 TC，
事务通信使用容器内 `8091` 端口，并映射到宿主机 `18091`；不提供内置 Web 控制台。

Start or refresh only the TC infrastructure:

```powershell
docker compose --env-file .env up -d --build seata-server
docker compose ps mysql nacos seata-server
docker compose logs --tail 100 seata-server
```

宿主机上的 TC RPC 地址为 `localhost:18091`。TC 在 `seata` 数据库中使用
`global_table`、`branch_table`、`lock_table`、
`distributed_lock` 和 `vgroup_table`。

商品、购物车、用户、交易、支付和 Marketing 六个服务均已接入 Seata AT 客户端，
统一使用事务组 `hmall_tx_group`，并通过 Nacos 发现 `seata-server`。五个 HMall 业务库
由初始化迁移创建各自的 `undo_log`，Marketing 则由 Flyway V27 创建该表。
交易创建、结算优惠券、订单取消和余额支付是当前全局事务入口；AT 只回滚同一 XID
下的关系型数据库写入，Redis、消息队列、Outbox 及外部 HTTP 副作用仍需业务补偿或最终一致性处理。

## 来源

- LuckyHub：`E:\ANotes\software\luckyhub`
- 黑马商城：`E:\ANotes\后端\后端开发\SpringCloud微服务—资料\day02-Docker\资料\hmall - 副本`
- MySQL：`E:\ANotes\后端\后端开发\SpringCloud微服务—资料\day02-Docker\资料\mysql - 副本`
- Nginx：`E:\ANotes\后端\后端开发\SpringCloud微服务—资料\day02-Docker\资料\nginx - 副本`

> LuckyHub 来源分支为 `feature/frontend-contract-remediation`，复制时来源工作树已有修改和未跟踪文件。本仓库提交保存的是实际工作树快照，不等同于来源仓库 HEAD。
