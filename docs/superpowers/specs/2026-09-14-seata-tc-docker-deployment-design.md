# Seata TC 数据库与 Docker 部署设计

## 目标

在现有 HMall Lottery Docker Compose 环境中部署单节点 Seata TC，并将事务协调数据持久化到当前 MySQL 8.4 实例。TC 使用现有 Nacos 3.1.1 作为注册中心，所有相关容器继续使用 Compose 自动创建的 `hmall-lottery_default` 网络。

## 已确认的运行环境

- 当前项目 MySQL 容器：`hmall-lottery-mysql-1`，镜像 `mysql:8.4`。
- 当前项目 Nacos 容器：`hmall-lottery-nacos-1`，镜像 `nacos/nacos-server:v3.1.1`。
- MySQL、Nacos 和现有项目服务均在 `hmall-lottery_default` 网络中。
- 旧 `hm-net` 是测试部署遗留网络，本次不使用、不修改。
- TC 镜像固定为 `apache/seata-server:2.4.0.jdk17`，以匹配项目 `seata/` 目录中的运行配置。

## 架构与启动顺序

1. `mysql` 启动并通过健康检查。
2. `schema-init` 在当前 MySQL 中创建 `seata` 数据库、专用账号和 TC 表。
3. `nacos` 在数据库初始化成功后启动并通过健康检查。
4. `seata-server` 在 `schema-init` 成功且 Nacos 健康后启动。
5. Seata 通过服务名 `mysql:3306` 连接 TC 数据库，通过 `nacos:8848` 注册服务。

所有服务均使用 Compose 默认网络，因此不新增顶层 `networks` 配置。

## 数据库设计

完善 `mysql/seata/seata-tc.sql`，使其可以安全重复执行：

- 创建字符集为 `utf8mb4` 的 `seata` 数据库。
- 创建或更新专用数据库账号 `seata_app`，密码由 Compose 通过会话变量注入。
- 仅授予 `seata.*` 所需权限，不让 TC 使用 MySQL root 账号。
- 创建 `global_table`、`branch_table`、`lock_table` 和 `distributed_lock` 四张 TC 表。
- 使用幂等写入初始化四条 `distributed_lock` 记录，重复运行不会产生主键冲突。

`schema-init` 将挂载并在 Nacos SQL 之后执行该脚本。这样现有数据卷无需删除，重新运行初始化服务即可补建 Seata 数据库。

## Seata 配置设计

保留 `seata/` 目录作为容器运行配置来源，并挂载到 `/seata-server/resources`。修改 `seata/application.yml`：

- DB 存储模式保持 `db`，数据库地址为 `mysql:3306/seata`。
- 数据库账号固定为 `seata_app`，密码从 `SEATA_DB_PASSWORD` 读取。
- Nacos 地址固定为容器服务名 `nacos:8848`，账号密码从环境变量读取。
- 控制台账号密码和安全密钥从环境变量读取，不再保留明文默认值。
- 控制台端口保持 `7099`，TC RPC 端口保持 `8091`。

Compose 中的 `seata-server` 将暴露可配置的宿主机端口，默认分别映射到 `17099` 和 `18091`，避免与常见本地端口冲突。容器内端口仍为 `7099`、`8091`。

## 配置与密钥

`.env.example` 新增以下配置，实际本地值放在被 Git 忽略的 `.env` 中：

- `SEATA_DB_PASSWORD`
- `SEATA_CONSOLE_USERNAME`
- `SEATA_CONSOLE_PASSWORD`
- `SEATA_SECURITY_SECRET_KEY`
- `SEATA_CONSOLE_PORT`
- `SEATA_RPC_PORT`

Nacos 继续复用已有 `NACOS_USERNAME` 和 `NACOS_PASSWORD`。

## 健康检查与验证

- 为 TC 添加健康检查，验证控制台 HTTP 端口可用。
- 使用 Compose 配置渲染验证服务依赖、端口、挂载和网络解析。
- 使用契约测试验证 SQL 表定义、专用账号、幂等初始化和 Compose 接线。
- 实际运行 `schema-init` 后查询 `seata` 库，确认四张表存在。
- 启动 TC 后检查容器健康状态、数据库连接日志，并通过 Nacos API 或控制台确认 `seata-server` 已注册。

## 边界

本次完成 TC 基础设施部署，不向各微服务添加 Seata 客户端依赖、事务组配置或 `@GlobalTransactional`。后续启用 AT 模式时，需要在每个参与分布式事务的业务数据库中创建 `undo_log` 表，并配置一致的事务服务组映射。
