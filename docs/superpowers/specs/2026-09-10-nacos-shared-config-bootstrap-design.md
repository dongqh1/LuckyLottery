# 五个商城服务接入 Nacos 共享配置与 Bootstrap 设计

## 目标

让 `item-service`、`cart-service`、`user-service`、`trade-service` 和 `pay-service` 在 Bootstrap 阶段从 public 命名空间、`DEFAULT_GROUP` 加载现有的 `shared.jdbc.yaml`、`shared.logger.yaml` 和 `shared.swagger.yaml`，并删除各服务本地与这三份配置重复的内容。改造后保留每个服务独立的端口、数据库标识、Swagger 元数据和业务配置。

## 范围

本次只修改五个拆分后的商城业务服务、Nacos 共享日志配置，以及为兼容 Windows 默认字符集而必须调整的 Nacos 共享 Swagger 占位符。

不修改：

- `hm-gateway`；
- Spring Boot 4 的 `marketing-service`；
- 旧单体 `mall-backend/hm-service`；
- `shared.jdbc.yaml` 及两份共享配置中未明确列出的其他内容。

## 当前状态

五个服务已经使用 `spring-cloud-starter-alibaba-nacos-discovery` 完成服务注册与发现，但尚未引入 Nacos Config 客户端和 Spring Cloud Bootstrap。数据库、MyBatis-Plus、日志格式和 Knife4j 公共结构目前重复存在于各服务的 `application.yaml` 中。

Nacos public 命名空间、`DEFAULT_GROUP` 已有：

- `shared.jdbc.yaml`：数据源 URL、驱动、账号/密码属性引用和 MyBatis-Plus 公共配置；
- `shared.logger.yaml`：日志级别、时间格式和日志文件路径；
- `shared.swagger.yaml`：Knife4j 公共元数据及 `${hm.swagger.*}` 服务级占位符。

其中 `shared.logger.yaml` 当前只为 `com.dongqh.payservice` 开启 DEBUG，不适合作为五服务共享配置。

## 方案选择

采用 Bootstrap + Nacos `shared-configs` 的中心化方案。

未采用以下方案：

- 加载 Nacos 后仍保留全部本地重复配置：本地高优先级配置会遮蔽远端值，无法可靠验证配置中心是否生效；
- 改用 `spring.config.import=nacos:`：虽然是新版本推荐方向，但不符合本次明确要求的 Bootstrap 接入方式，也不是当前 Spring Cloud Alibaba 2021.0.4.0 项目的最小变更。

## 配置架构

每个服务的 POM 增加：

- `com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config`；
- `org.springframework.cloud:spring-cloud-starter-bootstrap`。

每个服务新增 `src/main/resources/bootstrap.yaml`，负责在主应用上下文创建前提供：

- `spring.application.name`；
- `spring.profiles.active`，默认 `local`；
- `spring.cloud.nacos.server-addr`、`username` 和 `password`，继续支持现有环境变量和本地默认值；
- Nacos Config 的 `DEFAULT_GROUP`、YAML 文件格式及三项 `shared-configs`；
- 三项共享配置的动态刷新开关。

`application.yaml` 继续负责服务自身配置，包括端口、Nacos Discovery 行为、Feign、鉴权和业务属性，但移除已由 Nacos 提供的以下公共配置：

- `spring.datasource`；
- `mybatis-plus`；
- `logging`；
- `knife4j` 公共结构。

每个服务在 `application.yaml` 下保留一组 `hm.swagger` 元数据：

- 服务专属标题；
- 服务专属描述；
- 服务 Controller 包名。

具体值固定为：

| 服务 | 标题和描述 | Controller 包 |
| --- | --- | --- |
| `item-service` | 黑马商城商品管理接口文档 | `com.dongqh.itemservice.controller` |
| `cart-service` | 黑马商城购物车管理接口文档 | `com.dongqh.cartservice.controller` |
| `user-service` | 黑马商城用户管理接口文档 | `com.dongqh.userservice.controller` |
| `trade-service` | 黑马商城交易管理接口文档 | `com.dongqh.tradeservice.controller` |
| `pay-service` | 黑马商城支付管理接口文档 | `com.dongqh.payservice.controller` |

`application-local.yaml` 和 `application-dev.yaml` 继续提供各服务独立的 `hm.db.host`、`port`、`database`、`username` 和 `password`。这保证共享 JDBC 模板不会破坏数据库隔离。

## Nacos 变更

将 public 命名空间、`DEFAULT_GROUP` 下 `shared.logger.yaml` 的业务日志级别由：

```yaml
logging:
  level:
    com.dongqh.payservice: debug
```

调整为：

```yaml
logging:
  level:
    com.dongqh: debug
```

其余日志格式和文件路径保持不变。发布前读取旧内容，发布后重新读取并校验 Data ID、Group 和目标属性，避免误改其他配置。

Spring Cloud Alibaba 2021.0.4.0 的 Nacos YAML 解析器会使用 JVM 默认字符集把远端字符串转换为字节。在 Windows/GBK 环境中，`shared.swagger.yaml` 的中文默认值会导致 SnakeYAML 解码失败。五个服务已经各自提供标题和描述，因此将：

```yaml
title: ${hm.swagger.title:黑马商城接口文档}
description: ${hm.swagger.description:黑马商城接口文档描述}
```

调整为纯 ASCII 占位符：

```yaml
title: ${hm.swagger.title}
description: ${hm.swagger.description}
```

这不会改变五个服务最终展示的中文内容，并可避免要求 Windows 开发者额外设置 `-Dfile.encoding=UTF-8`。

## 配置加载流程

1. JVM 环境变量或默认值确定 Nacos 地址与凭据。
2. Bootstrap 上下文连接 Nacos Config。
3. 客户端按声明顺序加载 JDBC、日志和 Swagger 共享配置。
4. 活动 Profile 的 `application-local.yaml` 或 `application-dev.yaml` 提供服务专属 `hm.db` 值。
5. 主应用上下文使用合并后的属性创建数据源、MyBatis-Plus、日志和 Knife4j 配置。
6. 服务通过现有 Nacos Discovery 配置注册实例。

## 失败行为与安全性

- Nacos 地址和客户端凭据不写入新文件中的真实值，只引用 `NACOS_SERVER_ADDR`、`NACOS_USERNAME` 和 `NACOS_PASSWORD`，并保留现有本地默认值。
- 不把 `.env` 内容、数据库密码或 Nacos access token 输出到测试日志和最终报告。
- 共享 JDBC 配置缺失时，服务不应悄悄回退到另一数据库；启动验证必须确认远端属性源已加载。
- 自动化上下文测试显式禁用远端 Nacos Config 或提供独立测试属性，避免测试依赖正在运行的外部 Nacos。
- 工作区已有未提交改动必须保留；实施只编辑本设计列出的配置、依赖和测试文件。

## 测试设计

先增加配置契约测试并观察其因缺少新依赖、Bootstrap 文件和共享配置声明而失败，然后实施最小改动使其通过。契约测试验证：

- 五个 POM 都包含 Nacos Config 与 Bootstrap 依赖；
- 五个 `bootstrap.yaml` 都声明正确服务名、环境变量和三项可刷新的共享 Data ID；
- 五个 `application.yaml` 不再定义共享 JDBC、MyBatis-Plus、日志和 Knife4j 公共结构；
- 五个服务仍保留各自的 `hm.db` Profile 配置和 `hm.swagger` 服务元数据；
- 改造范围不包含网关、Marketing 和旧单体。

随后执行：

- 新增的配置契约测试；
- 现有基础设施契约测试；
- 五个服务的 Maven 测试；
- 五服务容器重新构建与启动；
- Nacos 已认证只读查询，确认共享日志配置发布成功；
- 容器日志或运行时端点检查，确认远端共享配置已加载且五个服务健康运行。

## 完成标准

- 五个服务均通过 Bootstrap 成功加载三份 Nacos 共享配置；
- 本地重复公共配置已删除，服务专属配置仍完整；
- Nacos 共享日志包范围覆盖 `com.dongqh`；
- Nacos 共享 Swagger 配置不含会触发 Windows/GBK 解码缺陷的非 ASCII 默认值；
- 自动化测试通过；
- 五个服务重新构建后能在现有 Compose 环境正常启动并注册到 Nacos；
- 最终报告逐文件列出项目改动，并单独列出 Nacos 远端变更和验证结果。
