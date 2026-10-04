# Marketing 身份与 RBAC 前置审计结果

数据库身份数据审计：已于 2026-10-01 执行（发现 3 条 `hmall:{id}` 身份在 `hm_user.user` 中缺少同 ID 记录，身份迁移暂停）
MySQL 服务端版本：8.4.11
审计方式：短时 `@localhost` 临时账号；授权范围仅为 `SELECT` on `luckyhub.*`、`hm_user.*`，并核验了 grants。
结果处理：只保留聚合计数；未输出或记录具体身份行、用户名或密码值。审计期间未修改业务数据。临时账号已删除，并确认不存在匹配账号。

## 2026-10-01 代码、运行态与数据库验证

- `tests/test_marketing_identity_rbac_preflight.py`：4 项通过，检查审计 SQL 只读且不选择或输出密码值。
- Marketing 定向 Java 测试（JDK 17）：15 项通过，覆盖权限上下文桥接、普通用户/管理员权限映射、未知角色拒绝、缺失身份拒绝，以及 401/403/503 错误映射。
- 匿名请求 `GET /api/marketing/admin/activities?page=1&size=10` 经 Nginx 到 Marketing 返回 `401 Unauthorized`。
- 本轮 Docker 只读检查：Item 为 `running / healthy`，重启计数 3，`OOMKilled=false`；Nginx 到 Gateway 的只读请求 `GET /items/page?pageNo=1&pageSize=1` 返回 HTTP 200。当前运行的应用和基础设施容器均为 healthy。Item 日志显示当前进程已成功启动，Flyway 校验通过且 `hm_item` schema 已是最新版本。
- Item 曾在依赖配置尚未就绪的启动阶段出现过一次数据源 URL 未配置错误，随后恢复并成功启动。当前没有持续超时或 OOM 证据；没有为此停止其他容器。
- 数据库聚合审计已实际运行，结果见下表。随后用另一个短时 SELECT-only 账号进一步分类 3 条投影异常，确认均为 `hm_user.user` 中缺少同 ID 记录；标记格式不匹配为 0。该账号也已删除并确认清理。首次连接失败及临时账号状态不明的情况已由后续成功审计和账号清理核验取代。

## 2026-10-01 源码路径排查

- `AuthenticationFilter.doFilterInternal` 从签名 JWT 建立 principal 后，直接调用 `HmallUserProjectionService.ensureActiveHmallUser`。
- `HmallUserProjectionServiceImpl.ensureActiveHmallUser` 在 Marketing 的 `sys_user` 不存在该 ID 时写入 `hmall:{id}` 占位投影，再补默认用户角色和优惠券；该调用链没有查询 `hm_user.user` 或调用 User 服务确认该 ID 仍存在。
- 因此，当前实现允许一个仍有效的 JWT 在用户库记录已不存在时创建或保留 Marketing 投影，与本次发现的三条同 ID 记录缺失相符；但仅凭现有聚合审计，不能断定这三条历史上确由此逻辑创建。
- 没有找到可将三条历史身份与具体登录请求对应起来的证据。需先受控核对映射 ID，再结合认证/删除历史判断原因；没有更改代码或身份数据。
## 用户总数与状态分布

| 用户表 | 总数 | 状态分布 |
|---|---:|---|
| `luckyhub.sys_user` | 10 | 状态 1：10 |
| `hm_user.user` | 8 | 状态 1：8 |

## 密码格式聚合

| 格式/类别 | 数量 |
|---|---:|
| BCrypt | 0 |
| 外部身份标记 | 9 |
| 空密码字段 | 0 |
| 其他格式 | 1 |

此项只记录分类计数，不保存密码明文或哈希值。

## 身份冲突聚合

| 冲突类别 | 数量 |
|---|---:|
| 相同 ID、不同用户名 | 0 |
| 相同用户名、不同 ID | 0 |
| `hmall:{id}` 投影缺少 `hm_user.user` 同 ID 记录 | 3 |
| `hmall:{id}` 标记与 `sys_user.id` 不匹配 | 0 |

聚合分类确认三条均是 `hm_user.user` 缺少相同 ID 的用户记录，而不是占位标记格式错误。具体 ID 和用户名未从审计查询返回，也未复制到审计文档。身份数据需在受控条件下人工核对后再决定处理方式。

## RBAC 孤立关系聚合

| 孤立关系 | 数量 |
|---|---:|
| `sys_user_role.user_id -> sys_user.id` | 0 |
| `sys_user_role.role_id -> sys_role.id` | 0 |
| `sys_role_permission.role_id -> sys_role.id` | 0 |
| `sys_role_permission.permission_id -> sys_permission.id` | 0 |

## 含 `user_id` 字段的表

`luckyhub` 中发现以下 18 张表含 `user_id` 字段：

`coupon_issue_record`、`draw_chance_account`、`draw_chance_ledger`、`draw_chance_reservation`、`lottery_draw_order`、`lottery_draw_record`、`mall_order`、`membership_grant_record`、`payment_order`、`points_account`、`points_ledger`、`points_redemption_order`、`shipping_address_snapshot`、`sys_user_role`、`user_benefit`、`user_coupon`、`user_membership`、`user_shipping_address`

## 审计边界与未决事项

- 临时审计账号仅限 `localhost`，`SHOW GRANTS` 确认只具有 `luckyhub.*` 与 `hm_user.*` 的 `SELECT` 权限及基础 `USAGE`；审计完成后已删除，且再次查询确认无 `codex_audit_*@localhost` 残留账号。
- 数据库只读审计没有执行写 SQL；未改账号 ID、用户名、密码、角色或权限，也未切换路由。
- 3 条 `hmall:{id}` 投影不一致是身份迁移的阻塞项。需要受控核对后再定处理方案；不得自动改 ID、合并账号或复制权限。
- RBAC 关联未发现孤儿记录，但这不证明各服务的运行时授权策略全部正确；代码与接口验收仍需按服务继续完成。
- 2026-09-24 的无认证连通性检查只确认 MySQL 客户端可用且 TCP 端口可达；不代表数据库登录或权限验证。其后曾有一次 MySQL 握手失败，现已由本次实际 SELECT-only 审计完成并取代.
