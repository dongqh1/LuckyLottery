# Marketing 微服务拆分会话交接总结

更新日期：2026-09-24
项目：`E:\ANotes\software\hmall-lottery`

## 当前结论

Marketing 还没有拆成十个业务微服务。根 Maven 聚合当前包含商城的 `item-service`、`cart-service`、`user-service`、`trade-service`、`pay-service`，以及尚未拆分的 `marketing-service`、`hm-gateway`、契约模块 `hm-api`；本次新增的 `hm-security` 是非部署型共享 JAR，不是业务微服务。目标十个业务服务为上述五个既有服务，加 `lottery-service`、`points-service`、`coupon-service`、`membership-service`、`fulfillment-service`；这五个新业务服务模块当前仍未建立。

身份/RBAC 前置审计的合同测试、只读 SQL 和脱敏结果报告已准备；2026-09-24 继续复核时确认 `E:\Mysql\bin\mysql.exe` 客户端可用，并通过只读容器清单确认当前运行的 MySQL 是 `hmall-lottery-mysql-1`（镜像 `mysql:8.4`，宿主机端口 `13307`）。仍没有已确认仅有 `SELECT` 权限并可读三个目标 schema 的审计凭证，因此数据库预检仍未运行。Marketing 业务代码、身份数据和 RBAC 未迁移。

## 本次执行状态（2026-09-24，hm-security 共享上下文切片）

- 根聚合加入非部署型 `hm-security` JAR，提供无 Spring/数据库依赖的 `TrustedUserContext`；`hm-common.UserContext` 保留原类名及 `setUser(Long)`、`getUser()`、`removeUser()` 静态方法，现委托到共享上下文。
- 新增上下文单元测试和双向兼容测试。桥接测试先按预期因两份独立 ThreadLocal 而失败，再在委托实现后通过。
- 为与 `hm-security` POM 中仅声明 JUnit 的依赖保持一致，计划及测试使用 JUnit 原生断言，不额外引入 AssertJ。
- 验证：`mvn -pl hm-security test` 通过 3/3；`mvn -pl :hm-common -am test` 通过，`hm-common` 9/9（并包含 `hm-security` 3/3）；最终 `python -B -m unittest tests.test_marketing_identity_rbac_preflight` 通过 4/4，`git diff --check` 通过，另对未跟踪计划与交接文档检查行尾空白无问题。
- 消费者验证 `mvn -pl :hm-api,:user-service -am test` 中，`hm-api` 编译通过、`hm-common` 9/9、`UserAdminServiceTest` 7/7；但 `UserServiceApplicationTests.contextLoads` 启动 Spring 测试上下文时，Seata 尝试连接未运行的 Nacos 注册中心并失败，故该 reactor 命令整体失败。本轮未启动 Compose 容器；测试启动了短暂的 Spring 上下文/Nacos 客户端并已退出。为遵守不启动状态化服务的约束，不再重跑会启动该上下文的测试。后续可用不启动上下文的编译/定向验证补齐消费者证据。
- 本次未连接 MySQL、未执行身份/RBAC 预检 SQL；结果报告继续保持“数据库预检未运行”，所有数据库数量和结构项仍为“未采集”。没有创建或修改账号，也未进行用户/RBAC 迁移。
- 本切片不包含 JWT、登录、权限查找、Servlet Filter、Gateway/Nginx 路由、Marketing 身份投影、数据库或 Flyway 改动；抽奖 Redis Lua、配额键、Redisson 锁、TTL 与对账逻辑未修改。

## 本次执行状态（2026-09-24，身份/RBAC 前置审计 Task 1）

- 目录/库存准备计划中的 Task 1 本轮重跑：`python -B -m unittest tests.test_marketing_identity_rbac_preflight` 退出码 0，4/4 项通过。`E:\Mysql\bin\mysql.exe` 客户端仍存在；当前环境中相关变量名仅见 `MYSQL5_HOME`、`MYSQL8_HOME`，未发现已明确确认仅有 `SELECT` 权限的审计凭证。三个目标 schema 的可读性未核验，身份/RBAC 预检 SQL 状态为 `未运行`，所有数据库聚合值仍为“未采集”。静态测试通过不等于数据库审计完成。

- 新增 `tests/test_marketing_identity_rbac_preflight.py`，先运行并确认因 SQL 文件缺失产生 3 个 `FileNotFoundError`，再新增 SQL 后运行通过 3 项合同测试。
- 新增 `scripts/migration/marketing-identity-rbac-preflight.sql`，只使用显式 schema 的 `SELECT` 查询，覆盖两库用户总数和状态分布、Marketing 密码格式聚合、ID/用户名冲突、`hmall:{id}` 投影差异、四类 RBAC 孤立关系及 `luckyhub` 中含 `user_id` 字段的表清单。
- 本次先运行 `python -B -m unittest tests.test_marketing_identity_rbac_preflight`，3 项合同测试通过；随后复核 SQL，确认未包含写操作关键字且所有查询均使用显式 schema。
- 初次按 `PATH` 和常见目录探测时未找到客户端，随后定位到 `E:\Mysql\bin\mysql.exe`；客户端版本为 MySQL 8.0.43。只读 `docker ps` 确认当前已有 `hmall-lottery-mysql-1`（镜像 `mysql:8.4`）运行，并将容器 3306 映射到宿主机 13307；同一栈的 Redis、RabbitMQ 也已在运行，本次未启动或修改它们。
- 项目根 `compose.yaml` 的 MySQL 映射默认是宿主机 13307；`marketing-service/compose.yaml` 定义了独立 MySQL，默认映射 3306，但该容器不在当前运行清单中。此前把 `localhost:3306` 当成当前审计目标不准确，审计目标端点应为 `localhost:13307`。
- 环境中没有可识别的审计凭证变量；现有 MySQL login-path 文件经 `mysql_config_editor print --all` 只读检查后返回空列表，也没有已确认只读的审计账号。因此未登录目标 Docker MySQL、未执行预检 SQL，也未采集服务端版本、聚合数量或 `user_id` 表清单。容器运行不等于账号权限及三个目标 schema 可读性已经确认。
- 更新脱敏报告 `docs/superpowers/audits/2026-09-24-marketing-identity-rbac-preflight-results.md`，保持“数据库预检未运行”，并准确记录当前 Docker MySQL 端点与只读凭证仍缺失；所有未取得的数据库结果继续标为“未采集”。
- 报告及交接文档更新后的最终验证结果：`python -B -m unittest tests.test_marketing_identity_rbac_preflight` 通过 3 项；`git diff --check` 通过；另对四个本次目标文件执行行尾空白检查，未发现问题。
- 在身份上下文切片开始前做过只读模块盘点：当时五个新业务服务模块与 `hm-security` 均不存在，Marketing 源码仍按 `auth`、`rbac`、`catalog`、`inventory`、`order`、`payment`、`lottery`、`points`、`coupon`、`membership`、`fulfillment`、`shipping` 等领域包组织。本次已新增共享 `hm-security` JAR；五个新业务模块及 Marketing 领域代码迁移仍未开始。
- 为使后续审计能记录服务端版本，按测试先行在预检 SQL 中新增只读 `SELECT VERSION() AS mysql_server_version`；预检连接示例已校正为当前 Docker MySQL 的宿主机端口 `13307`。该测试先因缺少版本查询失败，再在补充查询后通过；当前合同测试共 4 项。
- 未启动 Compose 或其他状态化服务，未创建/修改数据库账号，未使用应用账号代替只读审计账号；未生成或提交逐行账号映射。
- 未切换 Gateway/Nginx 登录或 RBAC 路由，未修改账号数据，未删除 Marketing 用户/RBAC 实现，未改写 Flyway V1–V28；抽奖 Redis Lua、配额键、Redisson 锁、TTL 和对账逻辑保持不变。

## 之前已完成

- 2026-09-23 Gateway 基础阶段已合入 `main`，当前提交为 `dfde5d7`（`docs: record bearer compatibility review`）。
- Nginx 营销 API 路径经 `hm-gateway` 转发，并通过共享 include 复用唯一精确登录片段；登录兼容路径暂时仍直达旧 Marketing。
- Gateway 保留营销旧服务低优先级兼容路由、支付回调路径兼容路由及物流回调精确 POST 例外；普通 Marketing API 由 Gateway 认证。
- Gateway 过滤器兼容裸 JWT 与大小写不敏感的 `Bearer` JWT，并原样保留下游 `Authorization`，继续清除伪造的 `user-info`。
- 已通过：`mvn -pl :hm-gateway -am test`（16 项）、`python -B -m unittest tests.test_infrastructure_contract`（52 项）、Docker Compose 配置校验以及 `git diff --check`。隔离 Nginx/Gateway 冒烟验证确认未认证营销请求到达 Gateway 并返回 401，而非 404。
- 拆分总设计为 `docs/superpowers/specs/2026-09-23-marketing-service-decomposition-design.md`；Gateway 阶段计划为 `docs/superpowers/plans/2026-09-23-marketing-api-gateway-foundation.md`。

## 不能跳过的现状与风险

- Marketing `JwtService.generate()` 使用 HS256，但 `JwtService.parse()` 调用只验证 RS256 的 `HmallJwtService`。新营销兼容登录必须由 `user-service` 签发 Gateway 与旧 Marketing 服务都能验证的 RS256 token，并测试 `user`、`role`、过期时间与会话 `jti`。
- 登录 JSON 也不同：Marketing 返回 `LoginView(userId, username, nickname, token, tokenType, expiresIn)` 并用 `ApiResponse` 包装；商城当前返回 `UserLoginVO(userId, username, balance, token)`。
- Marketing session Redis 协议为 `luckyhub:auth:session:{sessionId}`，值为用户 ID 字符串，TTL 等于 token TTL；RBAC 权限缓存为 `rbac:permissions:user:{userId}`，TTL 10 分钟。损坏缓存删除后回源、Redis 读失败按未命中回源、空集合缓存、权限变更即时及提交后再次失效都须保留。`SessionService.isValid` 当前没有生产调用点，不应借迁移顺手改变 token 校验语义。
- `luckyhub.sys_user` 与 `hm_user.user` 字段和状态值不同：前者有 `nickname`、状态 0/1；后者有 `phone`、`balance`、`role`、无 `nickname`，状态正常/冻结为 1/2。两库 ID/用户名冲突必须先出报告，禁止仅凭同名或相同 ID 自动合并。
- Marketing `sys_user` 仍被抽奖机会、积分、优惠券等业务使用。`HmallUserProjectionServiceImpl` 还负责建立 `hmall:{userId}` 投影、补默认角色并幂等发放结算券。移动 auth/RBAC 时需要替换这些耦合，不能直接删表或丢掉券发放效果。
- 共享安全边界仍需保留两种现有上下文语义：`hm-api` 和 `user-service` 使用 `mall-backend/hm-common` 的 `UserContext`；Marketing 使用包含 `userId/username/sessionId/role` 的 `LoginContext`。Marketing `AuthenticationFilter` 除解析 token 外，还调用 `HmallUserProjectionService.ensureActiveHmallUser`；抽取 `hm-security` 或调整过滤器时必须明确保留此投影/幂等发券职责和当前权限解析语义，不能借机绕过身份审计。
- 抽奖 Redis Lua 配额状态机、`draw:reservation:*`、`lock:draw:{activityId}:{userId}` Redisson 锁和 timeout 对账完全未触碰；后续 `lottery-service` 迁移必须按批准设计原样继承。

## 下一步

当前 MySQL 客户端可用，运行中 Docker MySQL 的宿主机端点为 `localhost:13307`。下一步需提供经验证只有 `SELECT` 权限且可读 `luckyhub`、`hm_user`、`information_schema` 的审计凭证；先核验授权与 schema 可读性，再运行 `scripts/migration/marketing-identity-rbac-preflight.sql`，把数据库执行日期与服务端版本、账号 ID/用户名冲突聚合数、商城用户投影差异、RBAC 孤立关系、状态/密码格式分布及含 `user_id` 的 Marketing 表清单回填到 `docs/superpowers/audits/2026-09-24-marketing-identity-rbac-preflight-results.md`。该表清单不等于逐行用户映射已完成。

结果报告只保存冲突类别和聚合数量，不包含逐行账号、手机号或密码散列。经人工批准的通用映射规则应写入后续身份/RBAC 迁移设计记录；如确需逐行映射，只能保存在受控迁移环境，不提交到 Git。

预检必须使用只读 MySQL 账号。当前数据库执行仍待完成；如后续发现身份歧义、`hmall:{id}` 投影异常或 RBAC 孤立关系，只记录聚合结果并暂停后续身份迁移，不自动修改 ID、合并账号或复制权限。审计结果和账号映射规则经人工复核前，不切换登录/RBAC Gateway 路由、不改任何身份数据、不删 Marketing 实现。

预检通过并确认映射后，再单独制定并实施 `user-service` 身份/RBAC 迁移；地址簿与 `item-service` 商品/库存留在后续阶段。拆分中的每个小任务完成后，更新本交接文档的状态与下一步，便于新会话接续。

十个业务服务的拆分可继续推进与身份/RBAC 数据迁移无关的领域切片；下一步应从总设计中选定一个业务服务，先写独立边界与迁移计划。`hm-security` 只是共享 JAR，不计入十个服务；`lottery-service` 等五个新业务服务仍未建立。若选择抽奖域，必须继续保持 Redis Lua、配额键、Redisson 锁、TTL 和对账逻辑不变。当前不得据此宣称 Marketing 已拆成十个服务，也不得开始 `user-service` 身份/RBAC 迁移。

## 工作区保护

- 当前 `main` HEAD：`dfde5d7a6c01d2d65db484edcfcca47c7f798b6c`。
- 本轮开始时 tracked 文件干净；已有未跟踪 `.codex-progress/`、`.hermes/`、`.superpowers/` 和本地草稿 `docs/superpowers/plans/2026-09-23-marketing-api-gateway-foundation.local-draft.md` 均属现有工作区内容，已保留，不要清理或覆盖。
- 没有推送远端或创建 PR。
