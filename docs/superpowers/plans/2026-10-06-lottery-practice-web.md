# 抽奖五服务练习网页实施计划

> **For agentic workers:** 按当前会话中已授权的任务范围执行；服务题目按 `dispatching-parallel-agents` 分别核对，交互与集成由主代理完成。

**Goal:** 将抽奖、积分、优惠券、会员、履约的真实代码整理为离线 HTML 练习，每题主要逻辑留空，并在题目下面折叠完整答案。

**Architecture:** 题库以三个 JSON 文件维护，网页外壳、样式和交互分别维护。Python 标准库构建脚本将它们嵌入一个可双击打开的 `index.html`；不需要联网或启动后端。

**Tech Stack:** HTML、CSS、原生 JavaScript、Python 标准库；浏览器验收使用已安装的 Python Playwright。

## 全局要求

- 服务范围仅 `lottery-service`、`points-service`、`coupon-service`、`membership-service`、`fulfillment-service`。
- 每个服务 5 道，共 25 道，来源必须是实际源码，答案注明方法或配置范围及现有依赖。
- 主要控制流、语句与注解由学习者补写；留下签名和中文编号步骤，避免只挖几个参数。
- 默认隐藏答案；每题下方用原生 `details` 点击展开。
- 代码可在网页编辑，切题与刷新保留当前浏览器草稿；导出草稿、恢复本题、标记完成均有明确按钮。
- 中文 UTF-8，PowerShell 7，修改限文档与网页验收，不改业务代码。
- 页面采用清晰的代码工作台：浅灰蓝背景、深蓝文字、青绿强调，离线系统字体；固定服务目录和题目区，移动端改为纵向。

## 文件与职责

- `docs/practice/bank/lottery.json`：抽奖核心、奖品管理、Redis 配额、锁与配置 5 题。
- `docs/practice/bank/points-coupon.json`：积分和优惠券共 10 题。
- `docs/practice/bank/membership-fulfillment.json`：会员和履约共 10 题。
- `docs/practice/web-shell.html`、`web-style.css`、`web-app.js`：网页结构、样式、交互。
- `docs/practice/build_web_practice.py`：校验题库结构、引用路径、编号，构建独立 HTML。
- `docs/practice/index.html`：用户直接打开的生成结果。
- `docs/practice/README.md`：新网页入口、使用方式、旧 Markdown 试作入口。
- `tests/test_practice_web.py`：离线浏览器交互验收。

## 执行步骤

- [ ] 先写浏览器验收：加载本地 HTML，确认答案默认折叠、编辑与切题保留草稿、刷新恢复、服务筛选、搜索和移动端无页面横向溢出。首次运行应因网页尚未存在而失败。
- [ ] 按五服务真实源码抽取 25 题，并核对方法签名、事务条件、幂等规则、组件与路径。
- [ ] 实现单页工作台和构建脚本；使用 `textContent` 渲染代码，题目数据嵌入 HTML，保存失败时提供导出草稿的方向提示。
- [ ] 构建后运行浏览器验收、数据与来源校验、JavaScript 语法校验，查看桌面和移动截图，按实际问题修正。
- [ ] 更新练习 README，打开完成的网页供用户检查。

## 验收命令

```powershell
python -m pytest tests/test_practice_web.py -q
python docs/practice/build_web_practice.py
node --check docs/practice/web-app.js
git diff --check
```

构建成功应显示每个服务 5 题与总体 25 题；网页验收应确认真实交互行为。草稿仅属于浏览器本地，不是 Java 运行结果，也不自动判断代码正确性。
