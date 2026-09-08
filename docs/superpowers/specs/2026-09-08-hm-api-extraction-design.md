# HM API 公共接口模块改造设计

## 目标

将新建的 `hm-api` 从 Java 17 / Spring Boot 4.1.1 可启动应用改造成与 `item-service`、`cart-service` 一致的 Java 11 / Spring Boot 2.7.12 纯 Maven 依赖模块。商品服务的远程调用契约只在 `hm-api` 维护一份，购物车服务通过该模块调用 `item-service`。

## 版本与 Maven 结构

- `hm-api` 继承 `mall-backend/pom.xml` 的 `com.heima:hmall:1.0.0` 父 POM。
- Java 版本为 11，沿用 Spring Boot 2.7.12、Spring Cloud 2021.0.3 和 Spring Cloud Alibaba 2021.0.4.0。
- 根聚合 POM新增 `hm-api`，并排在 `cart-service` 前面。
- `hm-api` 坐标统一为 `com.heima:hm-api:1.0.0`。
- `hm-api` 只依赖 `spring-cloud-starter-openfeign`；DTO 使用 Lombok，不引入 Web、Nacos、LoadBalancer、Knife4j 或数据库依赖。
- 删除 Spring Boot 打包插件，因为该模块不生成可执行服务。

## 代码边界

- `hm-api` 保留 `com.dongqh.hmapi.client.ItemClient` 和 `com.dongqh.hmapi.dto.ItemDTO`。
- `ItemClient` 返回 `hm-api` 自己的 `ItemDTO`，不能反向依赖 `cart-service`。
- 删除 `HmApiApplication`、`application.yaml` 和启动上下文测试。
- `cart-service` 添加 `com.heima:hm-api:1.0.0` 依赖，并删除本地重复的 `ItemClient`、`ItemDTO`。
- `CartServiceImpl` 和相关测试改用 `com.dongqh.hmapi` 下的公共类型。
- `cart-service` 使用 `@EnableFeignClients(basePackages = "com.dongqh.hmapi.client")`，明确扫描外部依赖包中的 Feign 接口。
- OpenFeign 和 LoadBalancer 运行依赖由 `cart-service` 保留；`hm-api` 只描述契约，不负责服务发现和运行配置。

## 调用流程

`cart-service` 注入 `hm-api` 提供的 `ItemClient`，OpenFeign 根据服务名 `item-service` 向 Nacos 查询健康实例，经 Spring Cloud LoadBalancer 发送 `GET /items?ids=...`，并将响应反序列化为公共 `ItemDTO`。

`item-service` 无需依赖 `hm-api`：它的 Controller 返回 JSON，只要字段契约保持兼容即可。这样提供者不会被 Feign 客户端依赖反向耦合。

## 错误处理

- 本次不新增 Feign fallback 或熔断器，保持当前失败语义：商品服务不可用或返回错误时，购物车查询返回调用异常。
- DTO 和路径契约通过自动化测试约束，避免迁移后再次出现错误包引用。

## 验证

- 契约测试确认根聚合包含 `hm-api`、版本基线正确且模块不可启动。
- 契约测试确认 `hm-api` 不依赖 `cart-service`，并且 `cart-service` 不再保留重复接口和 DTO。
- 使用 JDK 17 运行 Maven，但编译目标保持 Java 11。
- 执行 `mvn -pl :cart-service -am clean test`，验证 `hm-api`、`hm-common` 和 `cart-service` 在同一 Reactor 中成功构建。
