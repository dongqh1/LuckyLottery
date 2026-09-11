# JDK 17 与 Spring Boot 4.0 全仓统一升级设计

**日期：** 2026-09-11

**状态：** 已确认，等待实施计划

**范围：** 仓库内全部 Java/Maven 模块，包括回滚用 `mall-backend/hm-service` 与 `hm-gateway`

## 1. 目标

将当前同时存在的 Spring Boot 2.7/JDK 11 与 Spring Boot 4.1/JDK 17 配置统一为一套可维护的 JDK 17、Spring Boot 4.0.x 技术基线，并保持现有业务功能和外部契约不变。

“功能不变”具体包括：

- Controller URL、HTTP 方法、请求字段、响应 JSON、状态码不变。
- 商品、购物车、用户、下单、支付和营销业务规则不变。
- 服务名称、端口、数据库名称、Nacos Data ID 与 Group 不变。
- Gateway 路由、JWT 校验、匿名路径不变。
- Nginx 对外端口与前端使用方式不变。
- `Jack / 123` 等现有测试数据的登录行为不变。

本次不重构业务逻辑，不改变服务拆分边界，不改变默认 Docker Compose 的流量拓扑，也不借升级顺带开发新功能。

## 2. 当前状态

仓库目前存在两套技术基线：

- `mall-backend` 及其子模块、五个拆分微服务、`hm-api`、`hm-gateway` 使用 Spring Boot 2.7.12、Java 11、Spring Cloud 2021.0.3、Spring Cloud Alibaba 2021.0.4.0、MyBatis-Plus 3.4.3。
- `marketing-service` 已使用 Spring Boot 4.1.0、Java 17、MyBatis-Plus 3.5.17 Boot 4 starter、Springdoc 3.0.3 和 Jakarta API。

主要兼容断点是：

- Servlet 和 Validation 从 `javax.*` 迁移到 `jakarta.*`。
- Spring Boot 4 的 starter 与自动配置模块发生拆分。
- MyBatis-Plus 3.4.3 不适合作为 Boot 4 集成版本。
- 当前 Knife4j OpenAPI2 starter 依赖旧 Springfox 体系。
- Spring Cloud Alibaba 2025.1 不再支持 Bootstrap 上下文。
- Spring Cloud Gateway 的 starter 名称和配置属性层级发生变化。
- 六个 Dockerfile 仍使用 Temurin 11。
- `hm-gateway` 未加入根 Maven reactor。

## 3. 固定目标版本

采用保守且有官方对应关系的版本组合：

| 组件 | 目标版本/要求 |
| --- | --- |
| Java | 17 |
| Spring Boot | 4.0.8 |
| Spring Framework | 由 Spring Boot 4.0.8 管理 |
| Spring Cloud | 2025.1.0 |
| Spring Cloud Alibaba | 2025.1.0.0 |
| Nacos Client | 由 Spring Cloud Alibaba BOM 管理 |
| Nacos Server | 3.1.1 |
| MyBatis-Plus | 3.5.17 |
| Springdoc OpenAPI | 3.0.3 |
| Maven | 3.6.3 及以上 |
| Docker JDK/JRE | Eclipse Temurin 17 |

选择 Spring Boot 4.0.x 而不是 4.1.x，是为了采用 Spring Cloud Alibaba 官方明确适配的 Boot 4.0.x / Cloud 2025.1.x 组合。

## 4. Maven 与模块结构

根 `pom.xml` 作为全仓唯一版本基线：

- 继承 `spring-boot-starter-parent:4.0.8`。
- 定义统一的 `java.version=17`。
- 导入 Spring Cloud 2025.1.0 与 Spring Cloud Alibaba 2025.1.0.0 BOM。
- 集中管理 MyBatis-Plus、Springdoc 及确需显式固定的第三方库。
- 将 `hm-gateway` 加入根 `<modules>`。

`mall-backend/pom.xml` 保留 `com.heima:hmall:1.0.0` 坐标和父子关系，但改为继承根项目的技术基线。其余模块继续保留现有 artifactId、groupId 和模块依赖关系。`marketing-service` 改为继承根基线，最终由同一 Boot 4.0.8 父级管理。

依赖版本优先由 Boot、Cloud 和 Alibaba BOM 管理，删除会覆盖平台版本的旧硬编码项，例如旧 MySQL 驱动、Tomcat、Lombok 和 Spring Security RSA 版本；只有平台未管理且业务确实依赖的库才保留显式版本。

## 5. Jakarta、MyBatis 与自动配置迁移

只精确迁移 Java EE API：

- `javax.servlet.*` → `jakarta.servlet.*`
- `javax.validation.*` → `jakarta.validation.*`

不得全局替换全部 `javax`。`javax.crypto.*`、`javax.sql.DataSource` 等 JDK API保持原样。

MyBatis-Plus 统一使用：

- `mybatis-plus-spring-boot4-starter:3.5.17`
- `mybatis-plus-jsqlparser:3.5.17`

移除旧 `mybatis-plus-boot-starter:3.4.3`，保留现有 Mapper、分页规则、逻辑删除和 SQL 行为。数据库驱动改用 Boot 管理的 `com.mysql:mysql-connector-j`。

`hm-common` 的 Boot 2 `META-INF/spring.factories` 自动配置注册迁移为 Boot 4 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。自动配置类按运行环境增加条件：MVC/Servlet 配置只在 Servlet 应用加载，避免把 Tomcat/WebMvc 依赖带入响应式 Gateway；通用 JSON 和 MyBatis 配置仍按原语义启用。

## 6. API 文档兼容设计

旧 `knife4j-openapi2-spring-boot-starter` 替换为支持 Boot 4 的 `springdoc-openapi` 3.0.3。保留原有：

- 文档标题和描述。
- Controller 扫描包。
- 分组含义。
- Authorization 请求头说明。

为原 `/doc.html` 提供兼容入口并指向新的 Swagger UI，避免开发联调入口变化。OpenAPI 生成实现会从 Swagger2/Springfox 迁移到 OpenAPI3/Springdoc，但业务 HTTP API 不发生变化。

## 7. Nacos 配置迁移

Spring Cloud Alibaba 2025.1 不再支持 `bootstrap.yaml` 和 `spring-cloud-starter-bootstrap`，因此五个拆分服务执行以下等价迁移：

- 删除 Bootstrap starter。
- 将 `spring.application.name`、profile、Nacos 地址、用户名、密码和配置中心设置合并进 `application.yaml`。
- 使用 `spring.config.import` 显式导入原来的 `shared.jdbc.yaml`、`shared.logger.yaml`、`shared.swagger.yaml`。
- 显式导入原先可能由应用名隐式加载的 `${spring.application.name}.yaml`。
- 保持 `DEFAULT_GROUP`、刷新语义和 Data ID 不变。
- 本地缺少某个 Nacos Data ID 时保持当前可使用本地 profile 回退配置的能力。

地址约定保持：

- IDEA/宿主机 Java：`${NACOS_SERVER_ADDR:127.0.0.1:18848}`。
- Docker 内 Java：`NACOS_SERVER_ADDR=nacos:8848`。

Nacos Server 升级为 3.1.1。数据库升级采用幂等 schema 迁移，不删除 `mysql-data` 卷，不清空 `config_info` 等现有数据。Nacos 3 的控制台与客户端端口差异单独映射，应用客户端地址优先保持不变。

## 8. Gateway 迁移

Gateway 继续使用响应式 WebFlux 模型：

- starter 改为 `spring-cloud-starter-gateway-server-webflux`。
- 路由配置迁移到 Boot 4 / Gateway 5 对应的新属性层级。
- 不引入 Spring MVC/Tomcat runtime。
- 保留现有认证过滤器、JWT 公钥/密钥配置、Header 传递和匿名路径。

固定路由不变：

| 请求路径 | 目标服务 |
| --- | --- |
| `/items/**`, `/search/**` | `item-service` |
| `/carts/**` | `cart-service` |
| `/users/**`, `/addresses/**` | `user-service` |
| `/orders/**` | `trade-service` |
| `/pay-orders/**` | `pay-service` |

根 reactor 会构建和测试 Gateway，但默认 Docker Compose 仍允许 Nginx 按当前方式直连各微服务，不擅自改变部署流量拓扑。

## 9. Docker、IDEA 与本地运行

- 所有 Java 服务 Docker 构建镜像统一为 Maven 3.9.9 + Temurin 17，运行镜像统一为 Temurin 17 JRE。
- 保持服务容器端口、数据库环境变量、Redis 环境变量和依赖启动条件不变。
- IDEA Project SDK、Maven Runner JDK、各运行配置均以 JDK 17 为目标；不要求机器安装 JDK 11。
- 移除为旧 MyBatis-Plus 临时增加的 `--add-opens=java.base/java.lang.invoke=ALL-UNNAMED`。
- 本地 Nginx 仍监听 `localhost:18080`，前端资源和现有反向代理入口保持不变。

## 10. 分阶段实施

### 阶段 A：基线与保护

- 记录 Git 状态、目标文件清单和校验值。
- 将本次会修改且已有未提交内容的文件复制到 `.codex-progress` 作为仅供精确回退的快照。
- 运行当前可执行的 Maven 测试、Python 契约测试、前端契约测试并保存结果。

### 阶段 B：统一构建与基础 API

- 统一根 parent/BOM、Java 17、模块聚合和 Docker JDK。
- 完成 Jakarta、MyBatis-Plus、数据库驱动、Springdoc、Boot 自动配置迁移。
- 逐模块编译并运行不依赖外部基础设施的测试。

### 阶段 C：Cloud、Nacos 与 Gateway

- 升级 Spring Cloud/Alibaba。
- 将 Bootstrap 配置迁移到 Config Data。
- 升级 Nacos Server/schema。
- 迁移 Gateway starter、属性层级和响应式依赖边界。

### 阶段 D：整体回归

- 全 reactor 构建和测试。
- Docker Compose 配置解析、镜像构建和服务健康检查。
- Nacos 注册发现、共享配置加载、Gateway 路由验证。
- 本地 Nginx 前端联调与核心业务冒烟测试。

每个阶段通过后再进入下一阶段。失败时只撤销该阶段由本任务产生的修改，不覆盖用户升级前已有的工作区内容。

## 11. 验收标准

必须满足：

1. 所有 Maven 模块的 effective POM 使用 Java 17、Spring Boot 4.0.8，且不存在 Java 11、Boot 2、MyBatis-Plus 3.4.3 或 Bootstrap starter 残留。
2. 根项目构建包含 `hm-gateway` 并成功编译全部模块。
3. 当前环境可执行的单元、集成和契约测试通过。
4. Docker Compose 配置解析成功，所有 Java Dockerfile 使用 Temurin 17。
5. Nacos 启动并保留现有配置，服务名称和注册行为不变。
6. Gateway 原路由、JWT 与匿名访问规则通过测试。
7. `Jack / 123` 登录成功；商品、购物车、下单、支付和营销关键路径通过。
8. `localhost:18080` 前端能通过本地 Java 服务联调。
9. 无法因外部服务、凭据或运行环境验证的项目必须明确列出，不以编译成功代替功能验证。

## 12. 风险与控制

- **工作区已有大量修改：** 所有编辑使用小范围补丁；实施前快照；不执行 reset、checkout 或批量覆盖。
- **Nacos 2 → 3 schema：** 先比较官方 schema，采用增量、幂等迁移并保留数据卷。
- **Boot 4 模块化：** 以逐模块编译定位 starter 缺失，不通过无关的大包依赖掩盖问题。
- **Gateway 被 MVC 污染：** 使用 WebFlux 专用 starter，并对共享 Servlet 自动配置加条件。
- **文档组件变化：** 为 `/doc.html` 提供兼容入口，单独验证文档 JSON/UI，不影响业务 API。
- **功能回归：** 以现有测试、契约测试和真实 HTTP 冒烟对比为准，不仅检查能否启动。

## 13. 官方依据

- Spring Boot 4 系统要求：<https://docs.spring.io/spring-boot/system-requirements.html>
- Spring Cloud 与 Boot 兼容矩阵：<https://spring.io/projects/spring-cloud/>
- Spring Cloud Alibaba 版本关系：<https://sca.aliyun.com/en/docs/2025.x/overview/version-explain/>
- Spring Cloud Alibaba Nacos Config Data 迁移：<https://sca.aliyun.com/en/docs/2025.x/user-guide/nacos/advanced-guide/>
- MyBatis-Plus Boot 4 starter：<https://baomidou.com/en/getting-started/install/>
- Spring Cloud Gateway WebFlux starter：<https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/starter.html>
- Springdoc 兼容矩阵：<https://github.com/springdoc/springdoc.github.io/blob/master/src/docs/asciidoc/faq.adoc>
