# 用户管理中的抽奖机会管理实施计划

> **For agentic workers:** 按任务顺序执行，先编写失败测试，再实现并运行验证。

**Goal:** 将用户抽奖机会查询、调整和流水查看放进用户管理页的用户行操作中。

**Architecture:** 用户页增加“管理机会”弹窗，按需复用 lottery-service 现有账户、流水和调整 API。营销页移除独立的用户机会区域；不改后端和数据库。

**Tech Stack:** Vue 2、Element UI、Axios、Node.js `node:test`、Nginx Docker Compose。

## Global Constraints

- 使用现有会话令牌与管理员 API。
- 不为用户列表每行发起机会账户查询，不新增批量接口。
- 可用和预留余额分开展示，人工扣减只由后端现有逻辑校验。
- 保留奖品、活动、抽奖、Outbox 与 RabbitMQ 行为。

---

## 文件结构

- `nginx/html/hmall-admin/users.html`：用户表格入口和管理弹窗。
- `nginx/html/hmall-admin/js/user-draw-chances.js`：账户查询、流水分页、机会调整及错误提示。
- `nginx/html/hmall-admin/marketing.html`：移除独立机会管理区域。
- `nginx/html/hmall-admin/js/marketing.js`：移除机会面板专属状态与方法。
- `tests/test_users_draw_chances_frontend.cjs`：用户页入口、账户/流水和调整行为。
- `tests/test_lottery_admin_frontend.cjs`：确认营销页不再展示用户机会管理。

## Task 1：在用户页增加按需管理弹窗

- [ ] 先添加用户页契约和交互测试并确认旧代码失败。
- [ ] 增加“管理机会”列及弹窗，显示所选用户可用/预留余额、调整表单和含业务类型/业务编号的分页流水。
- [ ] 把抽奖机会交互放在单独脚本中，复用现有接口；对重试复用 adjustmentId，对不同表单内容生成新 ID。
- [ ] 运行 `node --test tests/test_users_draw_chances_frontend.cjs` 并确认通过。

## Task 2：移除营销页中的重复机会管理

- [ ] 调整 `test_lottery_admin_frontend.cjs`，要求营销页不再包含机会管理区。
- [ ] 删除 marketing 页面对应标记、状态和方法，不改变奖品、活动或抽奖逻辑。
- [ ] 运行 `node --test tests/test_lottery_admin_frontend.cjs` 并确认通过。

## Task 3：集成验证

- [ ] 运行两个相关前端测试套件及 `git diff --check`。
- [ ] 重建并重启本地 Nginx 服务，验证用户管理页和营销管理页均返回 HTTP 200。
