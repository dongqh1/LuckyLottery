# Marketing 身份与 RBAC 前置审计结果

记录日期：2026-09-24

审计状态：数据库预检未运行

2026-09-24 本轮重跑结论：静态合同测试 `python -B -m unittest tests.test_marketing_identity_rbac_preflight` 通过 4/4（退出码 0）；SQL 状态仍为 `未运行`。本轮确认 `E:\Mysql\bin\mysql.exe` 存在，当前环境中只发现 `MYSQL5_HOME`、`MYSQL8_HOME` 这两个相关变量名，未发现已明确确认为仅有 `SELECT` 权限的审计账号或凭证。因此没有条件核验三个目标 schema 的可读性，也没有连接或执行数据库 SQL。静态测试结果不代表数据库审计结果。

## 未运行原因

- 2026-09-24 继续复核时，通过 Windows 服务配置发现非标准安装目录中的客户端 `E:\Mysql\bin\mysql.exe`；客户端版本为 MySQL 8.0.43。
- 只读查看当前运行容器发现 `hmall-lottery-mysql-1` 使用 `mysql:8.4`，宿主机映射为 `13307 -> 3306`；当前审计目标应为 `localhost:13307`。容器清单还显示同一 Compose 栈的 Redis 和 RabbitMQ 正在运行，本次未启动或更改任何容器。
- `localhost:3306` 上的监听属于另一 `mysqld` 进程；Marketing 独立 Compose 中 MySQL 默认也使用宿主机 3306，但该容器不在当前运行清单中。此前将 3306 当作目标数据库端点并不准确。
- 尚未使用数据库账号连接 `localhost:13307`，所以 Docker MySQL 的服务端版本、`luckyhub`、`hm_user` 和 `information_schema` 的实际可读性均未验证。
- 当前环境未提供 `marketing_audit_ro` 或其他已确认仅有 `SELECT` 权限的审计凭证；审计凭证相关环境变量不存在，`mysql_config_editor print --all` 成功读取现有 login-path 文件但返回空列表。
- 按计划约束，本次没有创建或修改数据库账号，没有改用应用服务凭证，也没有启动 Compose 或任何现有状态化服务。

因此，下列数据库版本、聚合数量和结构清单均未采集；本报告不声称 SQL 已在数据库执行，也不声称身份映射已经完成。

## 执行信息

| 项目 | 结果 |
|---|---|
| SQL 脚本 | `scripts/migration/marketing-identity-rbac-preflight.sql` |
| 目标数据库 | `luckyhub`、`hm_user`、`information_schema` |
| 当前 Docker MySQL 端点 | `localhost:13307`（容器 `hmall-lottery-mysql-1`，镜像 `mysql:8.4`） |
| 数据库执行日期 | 未运行 |
| 本轮 SQL 状态 | 未运行（缺少已明确确认仅有 `SELECT` 权限的审计凭证；三个目标 schema 的可读性未核验） |
| MySQL 服务端版本 | 未采集（未登录数据库） |
| 只读账号 | 未提供 |
| 凭证配置探测 | 无审计环境变量；MySQL login-path 列表为空 |
| 先决条件探测日期 | 2026-09-24 |
| 客户端探测 | `E:\Mysql\bin\mysql.exe`，MySQL 8.0.43 |
| 数据库连通性 | Docker 容器端口映射已确认；尚未对 `localhost:13307` 执行账号连接测试 |
| schema 可读性 | 未确认（缺少已验证的只读凭证） |
| 原始逐行结果 | 未生成、未保存 |

## 身份与密码格式聚合

| 指标 | 数量/结果 |
|---|---|
| `luckyhub.sys_user` 用户总数 | 未采集 |
| `hm_user.user` 用户总数 | 未采集 |
| Marketing 状态分布 | 未采集 |
| HMall 状态分布 | 未采集 |
| BCrypt 格式数量 | 未采集 |
| `!HMALL_EXTERNAL_IDENTITY!` 标记数量 | 未采集 |
| 空密码字段数量 | 未采集 |
| 其他密码格式数量 | 未采集 |

密码检查脚本只返回算法格式的聚合计数，不返回密码散列值。

## 身份冲突聚合

| 冲突类别 | 数量 |
|---|---|
| 相同数字 ID、账号名不同 | 未采集 |
| 相同账号名、数字 ID 不同 | 未采集 |
| `hmall:{id}` 投影缺失或不匹配 | 未采集 |

脚本包含供受控环境临时复核的冲突明细结果集，但逐行账号、手机号、密码散列或账号映射不得写入本报告或提交到 Git。

## RBAC 关系完整性

| 孤立关系类别 | 数量 |
|---|---|
| `sys_user_role.user_id -> sys_user.id` | 未采集 |
| `sys_user_role.role_id -> sys_role.id` | 未采集 |
| `sys_role_permission.role_id -> sys_role.id` | 未采集 |
| `sys_role_permission.permission_id -> sys_permission.id` | 未采集 |

## Marketing 用户引用表

`information_schema.columns` 中 `luckyhub` schema 下含 `user_id` 字段的表清单未采集。即使后续获得该结构清单，它也只代表列级盘点，不代表各表的业务行已完成用户映射或核对。

## 未决事项与下一步

1. 提供可验证为仅有 `SELECT` 权限，并可读 `luckyhub`、`hm_user` 与 `information_schema` 的 MySQL 审计凭证；当前运行 Docker MySQL 的宿主机端口为 `13307`，客户端已可用。
2. 使用该只读账号连接 `localhost:13307`，先核验授权与三个目标 schema 的可读性，再执行只读 SQL，记录数据库服务端版本，并仅把上述类别的聚合数量和 `user_id` 表名清单回填到本报告。
3. 由人工复核身份歧义、状态转换、RBAC 孤立关系和各业务表用户引用后，再制定通用映射规则。
4. 审计结论获确认前，不自动修改 ID、合并账号、复制权限、清理 Redis、切换 Gateway/Nginx 登录或 RBAC 路由，也不删除 Marketing 的用户/RBAC 实现。
