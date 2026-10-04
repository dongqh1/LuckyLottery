# Marketing 微服务拆分会话交接总结

更新日期：2026-09-25
项目：`E:\ANotes\software\hmall-lottery`

## 当前结论

Marketing 还没有拆成十个业务微服务。根 Maven 聚合当前包含商城的 `item-service`、`cart-service`、`user-service`、`trade-service`、`pay-service`，以及尚未拆分的 `marketing-service`、`hm-gateway`、契约模块 `hm-api`；本次新增的 `hm-security` 是非部署型共享 JAR，不是业务微服务。目标十个业务服务为上述五个既有服务，加 `lottery-service`、`points-service`、`coupon-service`、`membership-service`、`fulfillment-service`；这五个新业务服务模块当前仍未建立。

## 本次执行状态（2026-09-24，Marketing trade/pay 边界设计）

- 用户于 2026-09-24 回复“继续”，确认边界设计并进入实施计划阶段。设计文档 `docs/superpowers/specs/2026-09-24-marketing-trade-pay-boundary-design.md` 已获确认，仍未实施；本轮新增只读审计准备计划 `docs/superpowers/plans/2026-09-24-marketing-trade-pay-migration-readiness.md`，尚未执行其中 SQL/测试文件创建步骤。
- 设计明确 trade 唯一拥有订单及订单状态，pay 唯一拥有支付生命周期，库存/优惠券/履约由各自领域服务拥有；Marketing 在未来单独批准的切换前仍是旧流程唯一写入方。没有改代码、数据库、账号、路由或 Marketing Flyway V1–V28，也没有启动 Compose/应用。
- 静态核对发现 Marketing 以字符串 `payment_no`/`order_no` 表示业务号且允许一个订单关联多条支付记录；现有 pay 模型使用数值 ID 和整数分金额。支付成功还会确认库存、核销优惠券、推进订单并创建实物履约单，因此不是单表搬迁。
- 仍待设计评审/业务确认：单订单多支付尝试的目标存储形态与查询语义；字符串与数值标识、状态和金额的映射；Seata 同步协调或 Outbox/补偿的一致性选择；超时取消与迟到支付成功的合同及补偿；旧支付 DTO/错误语义映射。未决项通过前不得写代码级迁移步骤或切流。
- 新读者复核未发现直接矛盾，确认上述问题仍是明确门禁。`python -B -m unittest tests.test_marketing_identity_rbac_preflight` 通过 4/4；`git diff --check` 退出码 0；新设计文档另经占位符与行尾空白检查通过。Git 输出仅有现存文件 LF/CRLF 转换提示。
- 静态检查发现根 `mysql/migrations/V007`、`V008` 是 schema-init 脚本且包含服务账号/权限变更；不得为本只读审计运行这些脚本或启动 Compose。
- 按设计流程尝试仅提交新设计文档，但 `.git/index.lock` 创建因权限被拒；未能提交，未暂存任何文件，未删除或清理锁文件。工作区其他已有修改和未跟踪文件均保留。
- 数据库身份/RBAC 与目录/库存前置审计仍按各自报告为“未运行”；本次未连接数据库或执行 SQL。

## 本阶段下一步

先按 `docs/superpowers/plans/2026-09-24-marketing-trade-pay-migration-readiness.md` 建立 SQL 静态合同测试与聚合预检脚本，运行静态验证；只有在已确认 SELECT-only 审计账号可读 `luckyhub`、`hm_trade`、`hm_pay`、`information_schema` 后才可执行 SQL，否则保持报告“未运行”。拿到脱敏聚合并批准支付尝试模型、一致性及迟到成功处理后，再单独编写 trade/pay 服务代码与数据切换计划。身份/RBAC 与目录/库存门槛仍有效；当前没有数据迁移或 Marketing 十服务拆分完成。

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

- 【实现阶段历史记录】新增 `tests/test_marketing_identity_rbac_preflight.py` 时，先确认因 SQL 文件缺失产生 3 个 `FileNotFoundError`，再补 SQL 并通过当时的 3 项合同测试；这不是本轮最终验证结果。本轮控制器后续重跑当前 4 项测试，4/4 通过。
- 新增 `scripts/migration/marketing-identity-rbac-preflight.sql`，只使用显式 schema 的 `SELECT` 查询，覆盖两库用户总数和状态分布、Marketing 密码格式聚合、ID/用户名冲突、`hmall:{id}` 投影差异、四类 RBAC 孤立关系及 `luckyhub` 中含 `user_id` 字段的表清单。
- 【实现阶段历史记录】身份/RBAC SQL 初版的合同测试为 3 项，添加服务端版本查询后扩为 4 项。该处 3/3 仅是较早的阶段性结果；本轮最终控制器重跑为 4/4。SQL 使用显式 schema 且只包含只读查询。
- 初次按 `PATH` 和常见目录探测时未找到客户端，随后定位到 `E:\Mysql\bin\mysql.exe`；客户端版本为 MySQL 8.0.43。只读 `docker ps` 确认当前已有 `hmall-lottery-mysql-1`（镜像 `mysql:8.4`）运行，并将容器 3306 映射到宿主机 13307；同一栈的 Redis、RabbitMQ 也已在运行，本次未启动或修改它们。
- 项目根 `compose.yaml` 的 MySQL 映射默认是宿主机 13307；`marketing-service/compose.yaml` 定义了独立 MySQL，默认映射 3306，但该容器不在当前运行清单中。此前把 `localhost:3306` 当成当前审计目标不准确，审计目标端点应为 `localhost:13307`。
- 环境中没有可识别的审计凭证变量；现有 MySQL login-path 文件经 `mysql_config_editor print --all` 只读检查后返回空列表，也没有已确认只读的审计账号。因此未登录目标 Docker MySQL、未执行预检 SQL，也未采集服务端版本、聚合数量或 `user_id` 表清单。容器运行不等于账号权限及三个目标 schema 可读性已经确认。
- 更新脱敏报告 `docs/superpowers/audits/2026-09-24-marketing-identity-rbac-preflight-results.md`，保持“数据库预检未运行”，并准确记录当前 Docker MySQL 端点与只读凭证仍缺失；所有未取得的数据库结果继续标为“未采集”。
- 【实现阶段历史记录】身份/RBAC 报告更新时记录的 3 项测试与空白检查，是当时的阶段性验证，不应视为当前最终结果；本轮最终控制器重跑 `python -B -m unittest tests.test_marketing_identity_rbac_preflight` 为 4/4 通过。
- 在身份上下文切片开始前做过只读模块盘点：当时五个新业务服务模块与 `hm-security` 均不存在，Marketing 源码仍按 `auth`、`rbac`、`catalog`、`inventory`、`order`、`payment`、`lottery`、`points`、`coupon`、`membership`、`fulfillment`、`shipping` 等领域包组织。本次已新增共享 `hm-security` JAR；五个新业务模块及 Marketing 领域代码迁移仍未开始。
- 为使后续审计能记录服务端版本，按测试先行在预检 SQL 中新增只读 `SELECT VERSION() AS mysql_server_version`；预检连接示例已校正为当前 Docker MySQL 的宿主机端口 `13307`。该测试先因缺少版本查询失败，再在补充查询后通过；当前合同测试共 4 项。
- 未启动 Compose 或其他状态化服务，未创建/修改数据库账号，未使用应用账号代替只读审计账号；未生成或提交逐行账号映射。
- 未切换 Gateway/Nginx 登录或 RBAC 路由，未修改账号数据，未删除 Marketing 用户/RBAC 实现，未改写 Flyway V1–V28；抽奖 Redis Lua、配额键、Redisson 锁、TTL 和对账逻辑保持不变。

## 本次执行状态（2026-09-24，目录/渠道库存前置审计 Task 3）

- Task 2 的只读目录/库存 SQL 合同测试（含服务端版本查询断言）最终通过 4/4；Task 2 reviewer 结论为可接受，无 Critical/Important findings。reviewer 提出的库存比较空值疑问已对照不可改写的 V9/V18 DDL 复核：库存等式比较涉及的 V9 字段均为 `NOT NULL`，V18 的 ledger 身份列不参与该等式，因此查询无需修改。
- MySQL 客户端已在既有检查中确认可用（MySQL client 8.0.43），但没有已确认仅有 `SELECT` 权限且可读 `luckyhub`、`hmall`、`information_schema` 的审计账号，schema 可读性未验证。因此未连接数据库、未执行目录/库存 SQL；没有启动 Compose 或其他状态化服务，也没有检查或使用应用凭证。
- 新增脱敏报告 `docs/superpowers/audits/2026-09-24-marketing-catalog-inventory-preflight-results.md`。SQL 执行日期和 MySQL server 版本未采集；产品/SKU 状态、销售模式、直接映射字段数、ledger 身份形态与两类孤立关系、预占状态、库存异常及 `hmall.item` 总数全部标记为“未采集”。这不表示数值为零或映射结构不存在；没有保存逐行结果、映射、凭证或秘密。
- 下一道门槛是取得对所需 schema 和 `information_schema` 已验证为只读的审计凭证，再确认可读性并执行已审阅 SQL。映射关系、item-service 迁移机制和服务端权限边界仍未验证；不得据此进行数据回填、身份/RBAC 迁移、路由切换或声称 Marketing 已拆成十个服务。

## 本次执行状态（2026-09-24，目录/渠道库存前置审计 Task 4 停止门槛）

- 身份/RBAC 与目录/库存两份 SQL 审计均为“未运行”：缺少已确认仅有 `SELECT` 权限的审计凭证，所需 schema 的可读性也未验证；所有数据库聚合仍为“未采集”。没有把未采集解释为零、没有推断 SKU→HMall item 映射存在/不存在/唯一，也没有保存逐行数据或映射。
- 因缺少映射聚合证据及经批准的业务映射规则，Marketing SKU→HMall item 关系尚不能批准或用于迁移；目前不能制定 item-service schema、数据回填或消费者迁移方案。
- 静态盘点显示 `item-service/pom.xml` 未声明 Flyway，模块资源中也没有 migration 目录。尚未为现有非空 HMall 数据库设计并验证迁移机制；在完成该设计和验证前，不添加会对目标库自动运行的迁移行为。
- 静态源码复核发现 `ItemController` 已有商品写路由，但未在 item-service 中验证到 `catalog:read`、`catalog:manage`、`inventory:manage` 的 fail-closed 服务端授权拦截或注解执行。`TrustedUserContext` 仅提供身份上下文，不等同于权限校验。因此不新增/暴露 Marketing 管理端点，也不改变 Gateway/Nginx 路由。
- 本次不执行数据库或服务操作，也无需文档门槛测试。前置审计在此停止：后续须先取得并验证只读审计凭证及 schema 可读性、取得脱敏聚合证据、明确并批准业务映射规则，并设计/验证 item-service 迁移机制与服务端授权；之后再单独编写代码级实施计划。
- 身份/RBAC、目录/库存数据与业务代码均未迁移；没有修改 ID、合并账号、复制权限、回填数据或切换路由。Marketing 用户/RBAC 实现及 Flyway V1–V28 未改；抽奖 Redis Lua、配额键、Redisson 锁、TTL 与对账逻辑保持不变。Marketing 尚未拆成十个业务微服务。

## 之前已完成

- 2026-09-23 Gateway 基础阶段的历史检查点为 `dfde5d7`（`docs: record bearer compatibility review`）；该提交是本轮 Marketing 前置审计开始时的历史基线，不是当前 `main` HEAD。
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

当前两个审计都停在未运行状态。下一步先取得明确仅有 `SELECT` 权限且能读取目标 schema 与 `information_schema` 的审计凭证，核验可读性后才可执行对应只读 SQL；只回填脱敏聚合。未取得映射聚合与获批业务规则前，不制定或实施 SKU→item 数据迁移。另须先设计并验证适用于现有非空 HMall 库的 item-service 迁移机制，以及 `catalog:read`、`catalog:manage`、`inventory:manage` 的 fail-closed 服务端授权；这些门槛满足后，再单独编写代码级实施计划。期间不切换登录/RBAC/API 路由，不迁移身份、目录或库存数据，不删除 Marketing 实现，也不开始 `user-service` 身份/RBAC 迁移。后续任何与身份迁移无关的服务切片仍须单独按总设计规划；`hm-security` 是共享 JAR，不计入十个业务服务，Marketing 也尚未完成十服务拆分。

## 工作区保护

- Marketing 前置审计启动时的历史基线（不是当前 HEAD）：`dfde5d7a6c01d2d65db484edcfcca47c7f798b6c`。
- 本轮开始时 tracked 文件干净；已有未跟踪 `.codex-progress/`、`.hermes/`、`.superpowers/` 和本地草稿 `docs/superpowers/plans/2026-09-23-marketing-api-gateway-foundation.local-draft.md` 均属现有工作区内容，已保留，不要清理或覆盖。
- 没有推送远端或创建 PR。

## 本次执行状态（2026-09-24，Marketing Trade/Pay 前置审计准备）

- 按 `docs/superpowers/plans/2026-09-24-marketing-trade-pay-migration-readiness.md` 完成 Task 1–3：新增 `tests/test_marketing_trade_pay_preflight.py`、显式 schema 的只读聚合 SQL `scripts/migration/marketing-trade-pay-preflight.sql`，以及脱敏结果报告 `docs/superpowers/audits/2026-09-24-marketing-trade-pay-preflight-results.md`。
- TDD 过程先运行静态测试，4 项因目标 SQL 尚不存在而按预期报 `FileNotFoundError`；补充 SQL 后，Trade/Pay 合同测试通过 4/4。随后 `python -B -m unittest tests.test_marketing_identity_rbac_preflight` 通过 4/4，`git diff --check` 退出码 0；Git 仅提示现有 LF 将来可能转换为 CRLF。新建 SQL、测试、报告的行尾空白检查无匹配。
- 数据库 SQL 状态仍为“未运行”：没有已确认仅有 `SELECT` 权限且可读取 `luckyhub`、`hm_trade`、`hm_pay`、`information_schema` 的审计账号，目标 schema 可读性未验证。因此未登录数据库、未采集 MySQL 服务端版本或任何数量/金额聚合；未把未采集解释为零。客户端存在或数据库容器运行均不能替代账号权限确认。
- 用户选择先核实只读账号后，本次只做了不登录数据库的宿主机客户端配置检查：`E:\Mysql\bin\mysql.exe` 和 `mysql_config_editor` 存在，已配置登录路径数为 0，常见数据库凭证环境变量名称匹配数为 0；未读取变量值、未尝试登录。另经只读 `docker ps` 确认 Docker MySQL 容器 `hmall-lottery-mysql-1`（镜像标签 `mysql:8.4`）已在运行，端口为宿主机 `13307` 到容器 `3306`；本次未启动/修改容器。MySQL 客户端在宿主机，数据库服务器在 Docker 中；这不证明审计账号权限或目标 schema 可读，仍须数据库负责人确认。
- 未启动 Compose、schema-init 或其他状态化服务，未探测或使用权限未知的应用凭证，未创建/修改账号。SQL 静态测试只验证脚本安全合同，不代表数据库审计已执行。
- Trade/Pay 未决事项仍包括多支付尝试存储与查询语义、业务号/ID/状态/金额映射、一致性机制、超时与迟到成功处置、旧接口兼容。若未来获准执行且聚合发现歧义、孤立关系或其他异常，只记录脱敏聚合并暂停迁移；不得自动映射/改 ID、合并数据、复制权限或切流。
- 本轮未改业务代码、数据库、账号、路由或 Marketing Flyway V1–V28；未开始 user-service 身份/RBAC 迁移，未删除 Marketing 实现；抽奖 Redis Lua、配额键、Redisson 锁、TTL 与对账逻辑未改。Marketing 仍未拆成十个业务微服务。
- 工作区原有修改与未跟踪文件均予保留；没有暂存或提交任何文件。

## 下一步（Trade/Pay 前置门槛）

先由数据库所有者提供已存在且确认仅有 `SELECT` 权限、并可读四个所需 schema 的审计账号；确认前不要执行 SQL。通过门槛后才可运行只读预检并把脱敏聚合回填到结果报告。出现重复尝试、孤立关系、金额/状态差异或 schema 缺失时暂停身份/订单/支付迁移决策。获得审计证据后，还须逐项批准支付尝试合同、一致性及迟到成功/补偿语义，再单独制定服务代码和切换计划；当前准备工作不代表 Trade/Pay 数据或 Marketing 领域代码已迁移。

## 本次审计准备收尾（2026-09-24）

- 身份/RBAC、目录/渠道库存、Trade/Pay 三类预检均已有显式 schema 的只读聚合 SQL、静态合同测试和脱敏结果报告。对应静态测试本次各通过 4/4；静态通过不代表任何数据库审计已执行。
- Docker 运行状态经只读 `docker ps` 检查：`hmall-lottery-mysql-1`（镜像标签 `mysql:8.4`）已运行，宿主机 `13307` 映射到容器 `3306`。宿主机上的 `E:\Mysql\bin\mysql.exe` 是客户端，不是本地数据库服务器；本次未启动或修改容器。
- 宿主机侧 MySQL 登录路径数为 0，常见数据库凭证环境变量名称匹配数为 0；未读取变量值或尝试登录。该检查不穷尽其他凭证管理器，也不证明账号权限。仍没有数据库负责人确认仅有 `SELECT` 权限并可读各审计目标 schema 的凭证。
- 三类数据库 SQL 均继续保持“未运行”，执行日期、服务端版本、用户/商品/订单/支付聚合与映射关系均未采集；这些未知值不代表零或无冲突。相关计划已标记静态准备与安全停止分支完成，未完成的数据库执行、数据判断和迁移决策步骤仍保留待办。
- 验证：`python -B -m unittest tests.test_marketing_identity_rbac_preflight`、`python -B -m unittest tests.test_marketing_catalog_inventory_preflight`、`python -B -m unittest tests.test_marketing_trade_pay_preflight` 均退出码 0、4/4 通过；`git diff --check` 退出码 0（仅有 LF/CRLF 提示），本轮涉及文档手工行尾空白检查无匹配。
- 未创建/修改数据库账号，未使用权限未知的应用账号，未启动 Compose 或其他服务；未切换路由、迁移业务/身份数据、修改 Marketing Flyway V1–V28，抽奖 Redis Lua、配额键、Redisson 锁、TTL 与对账逻辑未改。Marketing 尚未拆成十个业务微服务。

下一步仍是由数据库负责人确认一个现存的 SELECT-only 账号及其目标 schema 可读性，并通过受控方式配置凭证。确认前保持全部审计报告“未运行”；不要把密码发送到聊天或提交到 Git。

## 本轮继续（2026-09-24，身份/RBAC 审计连接前检查）

- 只做无认证连通性检查：MySQL 客户端 8.0.43 可用，`127.0.0.1:13307` TCP 可达。未建立 MySQL 登录会话、未执行 SQL；因此服务端版本、`luckyhub`/`hm_user`/`information_schema` 可读性和账号权限仍未验证。
- 身份/RBAC 报告继续为“未运行”，所有数据库聚合仍为“未采集”。下一步仍需数据库负责人确认现存账号仅有 `SELECT` 权限并可读目标 schema；不得改用权限未知的应用账号，也不得启动或修改 Compose 服务。

## 本轮继续（2026-09-24，Coupon 服务静态边界盘点）

- 按总设计，`coupon-service` 是身份/目录、订单/支付门槛之后的资产服务切片。本轮只读源码，没有建立新模块、迁移代码/数据、切路由或运行集成服务。
- 目标所有权为 `coupon_template`、`user_coupon`、`coupon_issue_record` 及券消费幂等记录。必须保留 `AVAILABLE -> LOCKED -> USED`、释放/过期状态、基于条件更新的并发竞争、`business_no` 幂等发券、超过 30 分钟的锁回收任务，以及当前结算折扣计算和 API 响应语义。
- 源码存在跨域直连：`CouponServiceImpl` 通过 `SysUserMapper` 检查 Marketing 用户状态、通过 `HmallCatalogClient` 验证商品范围；`CouponCheckoutServiceImpl` 读取目录商品和 `MembershipService` 折扣。商品/用户归并以及 item ID 映射未审计完成；会员服务按总设计晚于 coupon 拆分，因此 coupon 阶段必须明确过渡期会员查询契约，不能保留跨服务实现调用或跨库读。
- 调用方也有当前进程内耦合：订单创建预占券、取消释放券，支付成功同步核销；另有 `PaySuccessCouponListener` 消费 `PaySuccessMessage` 后核销。两条路径使用的订单标识表达不同，须在 Trade/Pay 事件契约中核实一致性与幂等，不能只移动类文件。
- Marketing 用户投影目前会直接为新投影用户批量写入默认 checkout 券及 issue 记录；抽奖 reward definition/snapshot 也直接查询 `CouponTemplateMapper`。用户迁移与后续 Lottery 迁移前，分别需要受控的幂等发券和模板查询契约，禁止由其他服务直接写/读 coupon schema。
- 静态验证目标已存在：`CouponServiceTests`、`CouponConcurrencyTests`、`CouponCheckoutServiceTests`、`CouponLockExpirySchedulerTests`、`PaySuccessCouponListenerTests`、`HmallCouponScopeTests`，以及 reward/order/payment 端到端测试。当前不应执行依赖数据库的测试或迁移步骤。
- 下一安全里程碑不是马上拆 coupon 代码：先完成并审阅身份、目录/库存、Trade/Pay 的只读审计与各自决策门槛；再为券服务定义 hm-api 调用/事件过渡契约、迁移与验收计划。遇到身份/商品映射歧义或支付成功标识不一致时暂停，不改 ID、不合并、不双写。
- 本轮验证：`python -B -m unittest tests.test_marketing_identity_rbac_preflight` 通过 4/4；`git diff --check` 退出码 0，仅有 LF/CRLF 提示。未运行 coupon 业务或服务集成测试。

## 本轮继续（2026-09-25，Coupon 非数据库行为基线）

- 在不启动 Compose、应用或外部服务的前提下，运行券域 Mockito/纯配置测试：`CouponCheckoutServiceTests`、`CouponLockExpirySchedulerTests`、`PaySuccessCouponListenerTests`、`PayCouponMessagingConfigurationTests`、`HmallCouponScopeTests`，共 8 项，全部通过。未运行需要数据库的 `CouponServiceTests`、`CouponConcurrencyTests`。
- Maven 默认使用 Java 8；该 JUnit 测试运行需显式使用 `C:\Program Files\Java\jdk-17.0.5`，并用 `-am` 将 `hm-api` 加入 reactor。此前未设置 JDK 17 或未包含依赖模块的尝试分别在测试启动/发现阶段失败，未产生有效测试结果；最终使用 JDK 17、离线缓存和 `-am` 的命令运行 8/8。
- 本轮仅建立券域非数据库行为基线，无业务代码、数据库、账号、路由或 Compose 变更。身份/RBAC、目录/库存及 Trade/Pay 数据审计仍未运行，资产服务正式迁移仍受总设计阶段门槛约束。

## 本轮继续（2026-09-25，Coupon 对外契约核对）

- 兼容 API 清单已核对：`GET /api/coupons`、`GET /api/coupons/{id}`、`POST /api/coupons/checkout/applicable`、`POST /api/coupons/checkout/reservations`、`PUT /api/coupons/checkout/reservations/{couponId}/used`、`DELETE /api/coupons/checkout/reservations/{couponId}`、`POST /api/admin/coupon-templates`、`POST /api/admin/coupons/issues`。本人券/checkout 使用 `COUPON_READ`，管理操作使用 `COUPON_MANAGE`；响应包装、分页默认值、校验语义需保持。
- 2026-09-25 源码追踪已厘清旧 Trade/Pay 优惠券链路：`OrderServiceImpl.createOrder` 在预占前设置数值 `Order.id` 并将该 ID 传给券客户端；Trade 的 `HttpCouponCheckoutClient` 将其转为十进制字符串作为券接口的 `orderNo`。Pay 通过 Trade 查询同一 `OrderPaymentDTO.id`，并把该 ID 写入 `PaySuccessMessage.orderId`；`PaySuccessCouponListener` 再将同一个 ID 转成字符串核销。因此，这条旧 Trade/Pay 路径的预占与事件核销键在源码层面一致。Marketing 自有现金订单则使用独立的字符串 `MallOrder.orderNo`，内部支付核销也使用该字符串；`PaySuccessMessage` 不含此 `orderNo` 或订单域标识。代码未证明这两套订单身份空间可互换，Coupon 拆分时必须保留域区分或另行批准规范化事件/引用契约；静态源码追踪也不代替数据库或运行时数据验证。
- 此次仅源码/契约静态核对，无接口、消息或权限实现变更。不得新增尚无调用方/提供方的空 `hm-api` 接口；待身份、商品和 Trade/Pay 门槛满足后，再根据真实消费者一起定义过渡契约并测试。

## 本轮继续（2026-09-25，Coupon 只读预检准备）

- 按已有 Coupon 静态边界与调用契约盘点，新增只读 SQL 合同测试 `tests/test_marketing_coupon_preflight.py`、聚合预检脚本 `scripts/migration/marketing-coupon-preflight.sql` 和脱敏结果模板 `docs/superpowers/audits/2026-09-25-marketing-coupon-preflight-results.md`。该工作只是数据库审计准备，不是 Coupon 服务拆分或数据迁移。
- SQL 仅含显式 schema 的 `SELECT`：统计券模板/用户券总数与分类分布、发券幂等键重复组数、模板/用户券/用户/订单间孤立及归属不一致聚合、HMall 商品范围字段与旧 Marketing 商品引用异常。业务幂等键只用于数据库内聚合，不输出其原值；脚本不返回逐行用户、券号、模板代码或订单号。要求只读账号可读 `luckyhub`、`hmall` 和 `information_schema`。
- 数据库预检实际状态仍为“未运行”，执行日期、MySQL 服务端版本和全部数据库聚合均为“未采集”：没有经确认仅有 `SELECT` 权限且可读目标 schema 的账号，也未验证 schema 可读性。本轮没有连接 MySQL、检查/读取凭证、启动 Compose 或任何状态化服务；客户端/容器可用性不等于权限确认。
- 验证：Coupon 合同测试先因 SQL 尚不存在按预期失败，补充 SQL 后 `python -B -m unittest tests.test_marketing_coupon_preflight` 通过 5/5；`python -B -m unittest tests.test_marketing_identity_rbac_preflight` 通过 4/4；目录/库存及 Trade/Pay 合同测试分别通过 4/4。静态测试不代表数据库审计已执行。文档更新后的 `git diff --check` 退出码 0，仅有 Git 关于现有 LF/CRLF 转换的提示。
- 未改数据库数据/账号、Marketing 业务实现、Gateway/Nginx 登录或 RBAC 路由、Flyway V1–V28；未开始 user-service 身份/RBAC、Coupon 或其他领域代码/数据迁移。未处理、覆盖或清理工作区已有未跟踪文件。抽奖 Redis Lua、配额键、Redisson 锁、TTL 与对账逻辑未改；Marketing 仍未拆成十个业务微服务。

下一步仍先取得并安全配置经数据库负责人确认的现存 SELECT-only 审计账号，核实目标 schema 可读后才执行预检并回填脱敏聚合。若无凭证或发现任何身份歧义、孤立关系/归属不一致、范围异常，则保持未运行/记录脱敏结果并暂停后续身份或资产迁移；不得自动修复、合并账号、改 ID 或复制权限。订单键静态追踪显示旧 Trade/Pay 路径内部使用一致的数值 Trade `Order.id`（以十进制字符串传给 Coupon），但 Marketing 自有现金订单的字符串 `MallOrder.orderNo` 属于另一订单域，当前事件不携带它。Coupon 切换前须定义带来源/订单域的引用与幂等事件契约，不能跨域假设两个值相等；总设计要求的身份、目录及 Trade/Pay 前序门槛也仍须满足。

## 本轮继续（2026-09-25，积分/抽奖/履约边界静态盘点）

- 对照总设计的表所有权，静态追踪 `points`、`lottery`、`shipping`/`fulfillment` 之间的直接依赖。`shipping` 是目标 `fulfillment-service` 内部包，不是第十一个服务；抽奖奖品库存 `ActivityPrizeInventoryService` 与商品/渠道库存是不同事实，继续归 Lottery，不应随商品库存迁往 item-service。
- `PointsRedemptionServiceImpl.create` 的普通 SKU 实物兑换分支当前仅由 Marketing 内部 `@Transactional` 包围，却依次调用目录快照、渠道库存预占/确认、积分扣减、地址快照/物流订单写入及兑换单状态转换（虚拟商品和 HMall offer 有分支差异）。拆到 item/points/fulfillment/user 所有者后已成为跨服务副作用，不能直接把当前事务注解套在远程调用上；需在实施前确定各分支的订单状态、幂等、失败补偿及同步响应语义。
- `LotteryRewardDispatchServiceImpl` 当前直接创建履约任务并回写抽奖权益的履约号；目标边界需保留确定性履约业务号、Outbox/消费幂等及权益状态回执。`PhysicalClaimServiceImpl` 与 `PhysicalClaimExpiryWorkerImpl` 当前通过 Lottery Mapper 读取抽奖记录/权益，并在同一处理里更新领取状态或返还抽奖奖品库存。拆库后不得保留跨服务 Mapper；需按总设计评审同步领取的 Seata AT，或另行批准并验证补偿流程。普通状态投影应由既定版本化事件回写 Lottery，而非履约服务直接改抽奖表。
- 本轮为源码/所有权静态检查，没有新增服务模块、接口/事件、SQL 或业务实现，没有执行数据库查询或启动状态化服务。身份/RBAC、商品/库存和 Trade/Pay 数据门槛仍未解除；抽奖 Redis Lua、配额键、锁、TTL 与对账逻辑未改。

当前可安全推进的是继续记录边界和可靠性合同；任何数据/代码迁移仍须先通过身份、目录/库存及订单/支付阶段门槛。尤其积分兑换跨 item/points/fulfillment 的事务与抽奖实物领取/过期返库跨 lottery/fulfillment 的一致性方案，须在相应服务代码迁移前形成可测试决策。

## 本轮继续（2026-09-25，会员服务兼容优先实施计划）

- 按已确认的兼容优先方向，新增 `docs/superpowers/plans/2026-09-25-marketing-membership-service-compatibility-migration.md`。计划先保留 `/api/memberships/**` 兼容语义和旧 Marketing 唯一写入者，再在有证据且经批准后切换到独立 `membership-service`；本轮没有开始会员代码/数据迁移、接口调用改造或路由切换。
- 将身份/RBAC 聚合审计设为硬门槛：只有现存账号已确认 SELECT-only 且可读目标 schema 时才允许运行；未运行仍记为未运行。身份歧义、投影异常或 RBAC 孤立关系将阻止以用户 ID 为键的数据迁移，不自动修 ID、合并账号或复制权限。
- 计划记录了当前购买接口缺少已支付凭证校验、同一 `purchase` 被付费与 Lottery 奖励共用、Membership 写事务直接锁 `sys_user`、`starts_at` 更新语义不明，以及 Lottery 数值产品 ID 与奖励快照/履约载荷一致性等硬决策门槛。未擅自设计订单验真或跨服务锁的替代实现。
- 兼容范围包括 Trade 与 HMall 的会员折扣读取/订单快照，以及 Lottery 的产品查询与履约后幂等发放。Pay 侧只按源码实际生产调用点处理，不把未发现调用方的同形客户端当成活跃依赖。计划不要求建立新 MySQL 集群；目标 schema 为设计中已有的 `hm_membership`。
- 本轮仅新增迁移计划并更新交接文档；未连接数据库、读取凭证、启动 Compose/服务、修改账号/数据、切 Gateway/Nginx 路由、改写 Flyway V1–V28、删除 Marketing 实现或改动 Lottery Redis/锁/TTL/对账逻辑。Marketing 仍未拆成十个业务微服务，也未开始 user-service 身份/RBAC 迁移。
- 验证：`python -B -m unittest tests.test_marketing_identity_rbac_preflight` 通过 4/4；`git diff --check` 退出码 0，仅显示工作区既有 LF/CRLF 转换提示。新计划无行尾空白。这些静态/文档检查不代表数据库审计执行或会员迁移完成。

下一步先满足已列出的身份/RBAC 审计准入条件并解决会员付费验真、奖励发放、用户停用并发、`starts_at`、产品身份及授权合同；任一项未决都保持代码/数据迁移暂停。只有阶段门槛和独立迁移回滚方案都获批准，才能按会员计划创建服务模块并逐步接入真实消费者。

## 本轮继续（2026-09-25，会员数据只读预检准备）

- 新增 `scripts/migration/marketing-membership-preflight.sql`、`tests/test_marketing_membership_preflight.py` 和 `docs/superpowers/audits/2026-09-25-marketing-membership-preflight-results.md`，仅为独立 `membership-service` 数据迁移准备审计合同，不是服务/数据迁移。
- SQL 全部为显式 schema 的 `SELECT`：先检查所需表元数据，再聚合会员产品状态/卡类型、用户会员有效/过期/异常及重复用户组、发放幂等键和有效期、Marketing 用户/会员产品孤立引用、Lottery 奖励定义与抽奖/权益快照产品 ID 不一致。用户 ID、业务幂等键、产品代码和逐行数据仅在数据库内部参与关联/分组，不作为结果输出；结果模板不含任何逐行映射。
- 结果报告明确标记“未运行/未采集”。没有确认 SELECT-only 凭证及目标 schema 可读性，因此本轮没有连接数据库或执行 SQL。检查 `hm_user.user` 是否存在仅是元数据检查，不能证明它与 `luckyhub.sys_user` 的用户 ID 对应；身份/RBAC 预检仍是独立硬门槛。
- 验证：`python -B -m unittest tests.test_marketing_membership_preflight` 通过 6/6；身份预检合同测试复跑通过 4/4；`git diff --check` 退出码 0（Git 仅提示工作区既有 LF/CRLF 转换）。这些静态测试不代表 MySQL SQL 已在服务端执行。
- 未启动 Compose、Docker 容器或应用；未改账号、数据库、Gateway/Nginx、Marketing 实现或 Flyway V1–V28，也未开始 user-service 身份/RBAC 或会员迁移。Lottery Redis Lua、配额键、Redisson 锁、TTL 与对账逻辑未动；工作区已有未跟踪文件均保留。

下一步仍须先验证现存审计账号已被确认只有 `SELECT` 权限、MySQL 客户端可用且 `luckyhub`、`hm_user`、`information_schema` 均可读；满足后按身份/RBAC 前置门槛执行对应审计，再单独考虑会员 SQL 预检。若不满足，继续保持“未运行”，不可借启动服务或使用权限未知账号绕过。

## 本轮继续（2026-09-25，会员服务边界决策稿）

- 新增 `docs/superpowers/specs/2026-09-25-marketing-membership-boundary-design.md` 作为兼容优先边界的审阅稿，并把已确认的提取方向与仍需业务/安全批准的决策分开；该稿不构成付费、退款、账号停用、权限或事件契约的批准，也没有授权实施。
- 对照总设计修正了会员奖励目标流：`fulfillment-service` 在验证奖励任务/快照后调用 Membership 幂等发放，成功后才提交履约成功并经 Outbox 通知 Lottery；Lottery 只更新自己的 `user_benefit` 投影，不在“履约已成功”后再次发放会员。会员计划的 Task 8 已同步为此边界。
- 源码发现当前两个侧面的数量/标识契约尚未对齐：Fulfillment 的 `MembershipGrantRequest` 只有履约号、用户、产品代码和时长，没有产品 ID/数量；Lottery 投影却按 `reward_quantity` 使用数值 `rewardTargetId` 逐份发放，并构造 `fulfillmentNo-M-index`。新边界必须决定冻结条款、产品引用、数量和幂等键长度/命名空间，避免漏发或重复延长会员。
- 付费流的现有 `PaySuccessMessage` 只有事件 ID、数字订单 ID、用户 ID、金额、券 ID和支付时间，没有订单域或会员产品/条款快照，不能单独作为会员开通依据。决策稿建议使用含不可变订单/产品快照的持久化支付成功事实；同时保留现有折扣读取/订单快照兼容，不复用当前无支付验真的公开购买接口。
- 本轮仅做源码核对及计划/决策文档更新，无代码、SQL、数据库、账号、路由、容器或服务变更。身份与会员数据库审计报告继续为“未运行”。

下一阶段仍受身份/RBAC 数据审计与会员域决策审批共同约束。尤其会员购买事件、奖励数量/幂等、停用用户的延迟发放语义及 `starts_at` 定义未获批准前，不开始 Membership 服务代码、数据回填或路由切换；Lottery Redis Lua、配额键、Redisson 锁、TTL 与对账逻辑继续保持不变。

## 本轮继续（2026-09-25，积分/履约非数据库测试盘点）

- 静态检查了四组可独立构造服务、使用 Mockito 的测试：`HmallPointsRedemptionServiceTests`（1 项）、`PhysicalBenefitClaimTests`（7 项）、`PhysicalClaimExpiryTests`（10 项）、`LotteryRewardDispatchTests`（3 项）；这四个测试类未使用 `@SpringBootTest`。过期测试还会读取本地 V17 迁移文件。需要 Spring 上下文/JDBC 的兑换、并发、库存和履约测试未运行。
- 尝试以 JDK 17、Maven 离线模式仅选择上述四个测试类（共 21 项）；Maven 在测试发现/执行前因其默认本地仓库 `C:\.m2\repository` 不可创建而退出，故这些 Java 测试实际状态为“未运行”，没有测试通过/失败数量。未改仓库外目录权限、未更改 Maven 缓存位置，也未联网拉取依赖；后续只有在存在可用且获准写入的本地依赖缓存后才重试。
- 本轮重新运行 `python -B -m unittest tests.test_marketing_identity_rbac_preflight`，静态合同测试 4/4 通过；这只验证预检 SQL 的静态约束，不代表数据库可读或身份/RBAC 审计已执行。数据库审计仍“未运行”，没有 MySQL 服务端版本或聚合结果。
- 本轮只做测试源检查和上述静态合同测试，没有启动 Compose、应用、Spring 上下文或数据库连接，没有修改业务代码、账号、数据库、路由或 Flyway。Marketing 尚未拆成十个业务微服务；积分兑换与 Lottery/履约间的事务、幂等和补偿门槛仍待设计，不开始身份/RBAC 迁移。

下一步仍应先补齐已确认仅有 `SELECT` 权限的审计账号和目标 schema 可读性，满足后执行身份/RBAC 只读聚合；Java 行为测试则待本地离线 Maven 仓库路径可用时重跑。测试或数据库审计发现身份歧义、投影异常或孤立关系时，只记录脱敏结果并暂停对应迁移，不自动改 ID、合并账号或复制权限。

## 本轮继续（2026-09-25，会员服务静态边界盘点）

- 按总设计，`membership-service` 唯一拥有 `membership_product`、`user_membership`、`membership_grant_record`（旧 V12）；该服务模块尚未创建，V1–V28 不改写。当前以 `user_id` 唯一约束维护每用户一条会员记录；同级别会员在有效期内续期从当前 `expires_at` 累加；其他情况以本次时间计算发放记录区间并替换会员等级/权益。注意现有会员行的 `starts_at` 只在首次插入时设置，续期/换级代码没有更新该字段；其对外含义尚未由现有测试覆盖，迁移前需确认。`business_no` 全局唯一，重放同一业务号不重复续期，用户/产品不一致时拒绝。
- 发现直接身份表依赖：`UserMembershipMapper.lockEnabledUser` 在会员写事务中对 `sys_user` 以状态 1 执行 `FOR UPDATE`，再锁会员记录并发放。拆分后不能让会员服务继续读取或锁定用户服务数据库；必须先完成用户 ID 对应关系审计，并定义用户停用与并发发放的跨服务语义，不能用远程状态检查冒充原本的同库原子锁。
- 会员权益读取有多个调用方：现有 `trade-service` 在创建订单及补选优惠券校验中调用 `/api/memberships/me`，并将会员折扣相关值写入订单快照；商城 `hm-service` 也保留同一路径的兼容客户端。Marketing 现金订单和券 checkout 目前进程内调用 `activeBenefit`。迁移时须统一保留折扣基点、过期判定、订单快照和异常/fail-closed 语义。`pay-service` 虽有同形客户端，但本次源码搜索未发现生产调用点，不能仅因类存在就把它视为真实消费者。
- Lottery 两个 reward 服务当前通过 `MembershipProductMapper` 读取产品：定义校验与奖励快照都需会员产品查询契约。冻结快照包含产品 ID/代码、级别和时长；奖励履约成功后的 `LotteryRewardProjectionServiceImpl` 再用 `rewardTargetId`（数值产品 ID）调用 `MembershipService.purchase`，业务号为确定性的 `fulfillmentNo-M-index`，随后推进权益状态。当前奖励投影只解析但不以 `MembershipFulfillmentPayload` 的代码/时长驱动本地发放，因此稳定产品 ID 映射、重放幂等和快照字段一致性必须在拆分前验证；不得让 Lottery 直接读会员库。
- 购买接口存在需要业务/安全负责人确认的契约缺口：`POST /api/memberships/purchases` 继承 `MEMBERSHIP_READ` 权限，只接收请求方提交的 `businessNo` 与产品 ID，再直接调用会员发放逻辑；该调用路径未见 Trade 订单状态或已支付凭证校验。总设计要求现金购买经 Trade/Pay，不能把当前接口不加验证地当作付费开通契约。当前同一个 `purchase` 入口还被 Lottery 奖励投影复用；正式拆分前须明确“经核验的付费购买”与“履约后的奖励发放”责任及各自幂等键。本轮只记录观察，没有改变接口、权限或业务行为。
- 测试盘点：`MembershipServiceTests`（3 项）和 `MembershipConcurrencyTests`（1 项）均标注 `@SpringBootTest` 并使用 JDBC，未运行；`MembershipControllerTests` 是 standalone MockMvc（1 项），本轮未运行。Maven 离线测试的本地仓库创建障碍仍见上一节。本轮只读源码、迁移和测试，不连接数据库、不启动服务、不修改业务实现、权限、路由或账号；身份/RBAC 数据审计仍为“未运行”。

下一安全步骤仍是取得并核验现存 SELECT-only 审计凭证；审计若发现用户身份映射歧义则暂停会员及其他以用户 ID 为键的迁移。即使审计通过，也先解决会员付费验真/奖励发放双入口与稳定产品引用合同，再写会员服务实施和兼容切换计划。Marketing 仍未拆成十个业务微服务。

## 本轮继续（2026-09-25，Points 只读预检准备）

- 新增 `scripts/migration/marketing-points-preflight.sql`、`tests/test_marketing_points_preflight.py` 和 `docs/superpowers/audits/2026-09-25-marketing-points-preflight-results.md`，为未来 `points-service` 迁移建立只读数据库审计合同；未创建服务或迁移数据。
- SQL 仅使用显式 schema 的 `SELECT` 和 `information_schema.tables` 元数据清单，要求只读账号可读 `luckyhub`、`hm_item`、`information_schema`。输出为积分账户/余额快照、流水方向及业务类型、冲正关系、兑换状态/商品类型、SKU/offer/商品引用、扣分与冲正流水匹配、实物地址快照/物流单关系等脱敏聚合；无逐行用户名、用户 ID、兑换号、业务号、商品名称或流水备注输出。`hm_item.item` 是仓库中 item-service 的数据库表，不根据名称或编码推断其它商品 ID 映射。
- 数据库状态继续为“未运行/未采集”：本轮未使用 MySQL 客户端登录、未连接或执行 SQL，因为没有经确认仅有 `SELECT` 权限且可读目标 schema 的现存审计账号；没有启动 Compose、Docker、schema 初始化、应用或其它状态化服务。存在客户端或服务容器不等于账号权限已核实。
- 测试先按预期因脚本和报告尚不存在而失败；补齐静态 SQL 合同和“未运行”报告后，`python -B -m unittest tests.test_marketing_points_preflight` 通过 5/5。数据库内容仍未验证。
- 若审计发现身份引用孤立、账户与最新流水余额不一致、重复幂等键/异常冲正、兑换商品引用或金额不一致、已结算流水不匹配、实物快照/物流关系异常，仅记录脱敏聚合并暂停对应迁移；不得自动改 ID、余额、流水、offer 库存或权限。用户 ID 与 `hm_user.user.id` 的对应仍由未运行的独立身份/RBAC 审计把关。
- 当前兑换实现仍在 Marketing 内直接连接库存、积分、地址快照和物流写入；按总设计拆为 Points 与 Item/Fulfillment 后，跨服务额度预留、事件幂等、失败补偿和同步响应仍需另行设计。本轮未改业务代码、账号、数据库、Gateway/Nginx 路由或 Flyway V1–V28；抽奖 Redis Lua、配额键、Redisson 锁、TTL 与对账逻辑未动。Marketing 仍未拆成十个业务微服务，亦未开始 user-service 身份/RBAC 或 points-service 迁移。

下一步只在数据库负责人确认现存 SELECT-only 凭证、MySQL 客户端可用且目标 schema 可读后执行本 SQL；若条件不满足继续保持“未运行”。出现任一身份歧义、孤立关系、余额/流水异常或履约链接异常时，先记录并暂停迁移，等待人工核查，不作自动修复。
