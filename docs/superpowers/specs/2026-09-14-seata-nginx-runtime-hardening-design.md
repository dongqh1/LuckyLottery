# Seata TC 与 Nginx 运行时加固设计

## 目标

修复当前 Docker Compose 环境中的两个运行时故障：Seata TC 使用 MySQL 8.4 `caching_sha2_password` 账号时持续出现公钥获取错误，以及 Nginx 在 `marketing-service` 或 `hm-gateway` 未运行时因启动阶段无法解析上游名称而退出。

## 已确认根因

- `seata_app` 使用 MySQL 8.4 默认的 `caching_sha2_password` 插件，但 Seata JDBC URL 禁用了 SSL 且未允许获取服务器公钥。连接池因此持续报告 `Public Key Retrieval is not allowed`。
- 仓库沿用了旧版四表配置，缺少 Seata 2.4 要求的 `vgroup-table` 属性和 `vgroup_table`，配置提供器因此持续报告 `seata.store.db..vgroup-table`。
- Nginx 配置中的静态 `proxy_pass` 会在启动阶段解析 Docker 服务名。`marketing-service` 已收到 SIGTERM 并正常停止，从 Docker DNS 消失，导致 Nginx 报 `host not found in upstream` 并以状态 1 退出。
- 当前 Seata 健康检查只验证控制台端口，不能代表数据库存储可用；当前 Nginx 没有健康检查。

## 修复设计

### Seata TC

保留专用账号 `seata_app`、最小权限和 `caching_sha2_password`，不降级 MySQL 认证插件。在 JDBC URL 中显式加入 `allowPublicKeyRetrieval=true`，并继续禁用本地 Docker 网络中的 TLS。补建 Seata 2.4 的 `vgroup_table` 并配置 `vgroup-table: vgroup_table`。契约测试必须验证这些参数和五表结构，防止后续配置回退。

重建 TC 后，以新容器启动时间为边界检查日志，要求不再出现数据库认证、连接池初始化或 Seata DB 配置异常；同时验证 TC 表、`seata_app` 活跃连接和 Nacos 注册。

### Nginx

在 `http` 块使用 Docker 内置 DNS `127.0.0.11`，把 Marketing 与 Gateway 的代理目标改成运行时解析变量。这样 Nginx 启动不依赖上游当时是否存在；上游缺失时只有对应请求返回 502，静态页面与其他可用路由仍然工作，上游恢复后 DNS 会自动重新解析。

Compose 为 Nginx 增加本机 HTTP 健康检查，验证 Portal 与 Admin 两个监听端口，而不是依赖上游业务接口。现有 `depends_on` 继续负责正常整栈启动顺序，但不再承担运行时容错职责。

## 测试与运行验证

1. 先新增契约断言并确认失败：Seata URL 必须允许公钥获取；Nginx 必须声明 Docker DNS、使用运行时上游变量；Compose 必须定义 Nginx 健康检查。
2. 实施最小配置变更并运行相关 Python 契约测试及 Compose 配置校验。
3. 重建 Seata，确认新日志无数据库认证或 DB 配置错误，数据库连接和 Nacos 注册存在。
4. 在 Marketing 保持停止的情况下重建 Nginx，确认容器健康、Portal/Admin 静态页返回 200，Marketing API 返回 502。
5. 启动并等待 Marketing 健康，确认无需再次重建 Nginx 即可恢复代理访问。
6. 运行基础设施、Seata、Nacos 与前端路由回归测试，记录最终容器及 Git 状态。

## 边界

- 不修改 `seata_app` 的认证插件或扩大数据库权限。
- 不在本次工作中引入 Seata 客户端、`undo_log` 或全局事务注解。
- 不修改 Marketing 业务逻辑；其历史退出是正常 SIGTERM，不是应用崩溃。
- 不提交 `.env` 或无关工作区改动。
