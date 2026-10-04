# Marketing 身份与 RBAC 归并前置审计实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变服务行为或数据库数据的前提下，产出 Marketing `sys_user` 与 `hm_user.user` 的身份冲突统计、RBAC 关系完整性和含 `user_id` 字段的业务表清单，为后续将 `auth`/`rbac` 迁入 `user-service` 建立安全切换门槛。

**Architecture:** 新增只读 MySQL 预检脚本，查询 `luckyhub` 与 `hm_user` 两个现有数据库；新增 Python 合同测试防止脚本意外包含写操作或遗漏关键对象；将执行日期、聚合计数、引用表清单与未决事项记入脱敏结果报告。该任务不创建账号映射、不迁移数据、不更改 API、Gateway、Nginx 或 Redis。

**Tech Stack:** Java 17 / Spring Boot 4.0.8 项目；MySQL 8；Python 标准库 `unittest`；PowerShell 7。

## Global Constraints

- 业务设计依据：`docs/superpowers/specs/2026-09-23-marketing-service-decomposition-design.md`。
- 不得改写 Marketing Flyway V1–V28；本任务不新增服务启动迁移。
- Marketing 的 `sys_user` 与商城 `hm_user.user` 是不同数据库中的身份数据；禁止仅凭相同数字 ID 或用户名自动合并账号。
- 审计脚本只允许读取 `luckyhub`、`hm_user` 和 `information_schema`；不得输出密码散列明文，不执行 INSERT、UPDATE、DELETE、DDL 或 Redis 操作。
- 如存在身份歧义、ID 冲突、孤立关联或无法解释的用户引用，后续迁移必须暂停并先形成经确认的映射规则。
- 不触碰抽奖 Redis Lua 脚本、Redisson 锁键、锁粒度、TTL 或任何在途 Redis 状态。
- 保留工作区已有未跟踪内容；不覆盖 `docs/superpowers/plans/2026-09-23-marketing-api-gateway-foundation.local-draft.md`。

---

## 现状和审计边界

- Marketing 用户表位于 `luckyhub.sys_user`，字段包含 `id`、`username`、`password`、`nickname`、`status`；商城用户表位于 `hm_user.user`，字段包含 `id`、`username`、`password`、`phone`、`status`、`balance`、`role`，当前没有 `nickname`。
- Marketing 的 `sys_user` 不只是登录/RBAC 数据：抽奖机会、积分、优惠券等业务代码仍引用它；`HmallUserProjectionServiceImpl` 还会插入商城用户投影、默认角色并幂等发放结算券。
- `hm_user.user.status` 使用正常/冻结编码 1/2；Marketing `sys_user.status` 使用启用/禁用编码 1/0。未经显式转换，不得直接复制状态值。
- Marketing `JwtService.generate()` 当前用 HS256，而 `JwtService.parse()` 委托给只验证 RS256 的 `HmallJwtService`；兼容登录切换必须另行实现并测试 RS256 契约，不能把当前生成结果当作有效基线。
- Marketing 登录响应 `LoginView` 与商城 `UserLoginVO` 不同；现有 Redis session 键为 `luckyhub:auth:session:{sessionId}`，值为用户 ID 字符串，TTL 等于 token 有效期。RBAC 权限缓存键前缀为 `rbac:permissions:user:`，TTL 10 分钟。
- 本计划只完成切换前审计，不移动 `auth`、`rbac`、地址簿、商品目录、库存或任何业务表的所有权。

### Task 1: 建立只读身份/RBAC 预检脚本和合同测试

**Files:**
- Create: `scripts/migration/marketing-identity-rbac-preflight.sql`
- Test: `tests/test_marketing_identity_rbac_preflight.py`

**Interfaces:**
- Consumes: 只读访问 `luckyhub`、`hm_user` 和 MySQL `information_schema` 的数据库账号。
- Produces: 一组只读结果集，以及 `docs/superpowers/audits/2026-09-24-marketing-identity-rbac-preflight-results.md` 中的脱敏聚合总结；不保存密码散列、手机号或逐行账号映射，不生成或应用映射。

- [x] **Step 1: 先写失败的脚本安全合同测试**

在 `tests/test_marketing_identity_rbac_preflight.py` 中使用 `unittest` 和 `pathlib` 加载 SQL 文件，增加以下断言：

```python
import re
import unittest
from pathlib import Path


class MarketingIdentityRbacPreflightTests(unittest.TestCase):
    script_path = Path(__file__).resolve().parents[1] / "scripts/migration/marketing-identity-rbac-preflight.sql"

    def test_script_exists_and_checks_both_identity_databases(self):
        sql = self.script_path.read_text(encoding="utf-8")
        self.assertIn("luckyhub.sys_user", sql)
        self.assertIn("hm_user.user", sql)
        self.assertIn("information_schema.columns", sql)

    def test_script_contains_only_read_queries(self):
        sql = re.sub(r"(?m)^\s*--.*$", "", self.script_path.read_text(encoding="utf-8"))
        sql = re.sub(r"/\*.*?\*/", "", sql, flags=re.S)
        self.assertNotRegex(sql, r"(?i)\b(INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP|TRUNCATE|RENAME|CALL)\b")

    def test_script_reports_identity_and_rbac_conflicts_without_selecting_passwords(self):
        sql = self.script_path.read_text(encoding="utf-8").lower()
        for required in ("username", "sys_user_role", "sys_role_permission", "status", "password regexp"):
            self.assertIn(required, sql)
        self.assertNotRegex(sql, r"(?is)select\s+[^;]*\bpassword\s*(,|from)")


if __name__ == "__main__":
    unittest.main()
```

- [x] **Step 2: 运行合同测试，确认它因脚本缺失而失败**

Run: `python -B -m unittest tests.test_marketing_identity_rbac_preflight`

Expected: FAIL，原因是预检 SQL 文件尚不存在；不得通过跳过测试或放宽断言来让它通过。

- [x] **Step 3: 编写只读 SQL 结果集**

脚本按独立结果集输出以下内容，不打印密码散列或个人凭证：

1. 两张用户表的总数、状态分布以及 Marketing 密码散列算法前缀的聚合计数。
2. 相同数字 ID 但账号名不一致的行，以及相同用户名但 ID 不同的行；Marketing `hmall:{id}` 投影账号单独核对其 ID 是否存在于 `hm_user.user`。
3. `sys_user_role.user_id` 到 `sys_user.id`、`sys_user_role.role_id` 到 `sys_role.id` 的孤立关系；以及 `sys_role_permission.role_id` 到 `sys_role.id`、`sys_role_permission.permission_id` 到 `sys_permission.id` 的孤立关系。
4. `information_schema.columns` 中 `luckyhub` 含 `user_id` 字段的表清单。这只是结构盘点，不代表已核对每个表内的用户行或映射结果；后续迁移计划须根据此清单逐表制定只读计数/映射检查。

结果集的核心查询形态如下；审计脚本须使用这些只读查询扩展完整报告：

```sql
SELECT m.id AS marketing_user_id, m.username AS marketing_username,
       h.id AS hmall_user_id, h.username AS hmall_username
  FROM luckyhub.sys_user AS m
  JOIN hm_user.user AS h ON h.id = m.id
 WHERE m.username NOT LIKE CONCAT('hmall:', h.id)
   AND m.username <> h.username;

SELECT m.id AS marketing_user_id, m.username AS marketing_username,
       h.id AS hmall_user_id
  FROM luckyhub.sys_user AS m
  JOIN hm_user.user AS h ON h.username = m.username
 WHERE h.id <> m.id;

SELECT table_name, column_name
  FROM information_schema.columns
 WHERE table_schema = 'luckyhub'
   AND column_name = 'user_id'
 ORDER BY table_name;
```

密码算法只输出聚合计数，例如 BCrypt 格式与其他格式各有多少账号；不能输出 `password` 字段值。脚本必须使用显式数据库名，不能依赖当前连接默认 schema。

- [x] **Step 4: 运行合同测试并检查补丁**

Run: `python -B -m unittest tests.test_marketing_identity_rbac_preflight`

Expected: 所有预检脚本安全合同通过。另运行 `git diff --check`，预期无空白错误。

- [x] **Step 5: 在有只读数据库凭证的环境运行预检（本次命中安全停止分支）**

Run: `Get-Content -Raw -Encoding UTF8 scripts/migration/marketing-identity-rbac-preflight.sql | mysql.exe --host=localhost --port=13307 --user=marketing_audit_ro --password --batch --raw`

Expected: 只返回审计结果集且 MySQL 用户 `marketing_audit_ro` 只有 SELECT 权限。把执行日期、数据库版本、各类冲突/孤立关系计数、`user_id` 表清单及未决项记入 `docs/superpowers/audits/2026-09-24-marketing-identity-rbac-preflight-results.md`；只写聚合数据，不写逐行账号、手机号或密码散列。若本机没有这个现成的只读账号或数据库未运行，不创建/修改账号、不启动 Compose 中已有的状态化服务；在报告中标记未运行及原因，不伪称预检已完成。

本次未执行上述命令：虽有宿主机 MySQL 客户端且 Docker MySQL 已运行，但没有负责人确认的 SELECT-only 审计账号，目标 schema 可读性未验证。已按安全停止分支将报告保留为“未运行”。

### Task 1 验收门槛

- 测试确认脚本同时检查 `luckyhub` 与 `hm_user`，且没有写操作关键字。
- 报告能统计 ID/用户名不一致、商城用户投影不匹配、RBAC 孤立关系、状态分布、密码散列算法前缀分布，并列出 Marketing 用户引用表；它明确不声称已完成业务行级映射。
- 冲突类别和聚合数量必须保留在结果报告中；原始逐行账号结果不提交到仓库或普通交接文档。不能自动改 ID、自动合并账号、复制权限或清除 Redis。
- 经人工批准的通用映射规则写入后续身份/RBAC 迁移设计记录；逐行账号映射（如确需）只保存在受控迁移环境，不进入 Git。
- 审计结论经人工确认前，不添加 Gateway 登录白名单、不修改 Nginx 登录例外、不删除 Marketing `sys_user`/RBAC 实现。

## 后续拆分顺序

1. 根据预检结果确认账号身份映射、昵称与状态转换、历史业务 `user_id` 是否可保持不变；若存在歧义，先由项目所有者决定。
2. 单独设计并实施 `user-service` 的营销兼容登录、RS256 `user`/`role` claims、会话 `jti`、登录/登出响应契约。
3. 将 RBAC 数据与管理 API 迁入 `user-service`；抽取唯一的 `hm-security` 实现，并让 Marketing 通过明确的 `hm-api` 权限契约进行授权，不复制权限缓存。
4. 逐个处理 Marketing 业务代码对 `sys_user` 的读写依赖，保留首次商城用户投影时的结算券幂等发放行为。
5. 完成数据核对、接口/Redis 契约测试和回滚演练后，才切换 Gateway/Nginx 登录及 RBAC 路由并移除旧实现。
6. 地址簿归并与 `item-service` 商品/渠道库存归并继续作为后续独立计划；抽奖 Redis/Lua/Redisson 作为独立核心阶段，不纳入本计划。

## 自审

- 本计划只覆盖身份/RBAC 切换前置审计，没有把身份、地址、商品库存混为一阶段。
- 所有数据库查询限定只读；没有计划清表、改写用户 ID、并行双写或路由切换。
- 数据模型差异、JWT 算法不一致、Redis session/cache 契约以及 Marketing 业务对 `sys_user` 的调用依赖均已显式记录。
- 新会话应先执行 Task 1；只有在审计输出和身份映射决策可供复核后，才编写身份/RBAC 实施计划。
