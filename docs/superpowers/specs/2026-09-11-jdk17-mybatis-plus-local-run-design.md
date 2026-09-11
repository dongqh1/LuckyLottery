# JDK 17 下商城微服务本地运行兼容设计

## 目标

在不安装 JDK 11、不升级 MyBatis-Plus、不改动 Marketing 服务和 Nginx 配置的前提下，使 IDEA 中使用 JDK 17 启动的商城 Java 11 微服务能够正常执行 MyBatis-Plus Lambda 查询，并恢复本地前后端登录联调。

## 根因

商城微服务的 Maven 编译目标为 Java 11，但 IDEA 项目和当前运行进程使用 JDK 17。项目使用的 MyBatis-Plus 3.4.3 会反射访问 `java.lang.invoke.SerializedLambda`；JDK 17 默认禁止该访问，导致 `SerializedLambdaMeta` 初始化失败。登录页把所有请求异常统一显示为“用户名或密码错误”，掩盖了后端实际返回的 HTTP 500。

数据库中的 `Jack` 用户仍然存在、状态正常，其 BCrypt 密码哈希仍与明文 `123` 匹配。

## 方案

在 IDEA 的本地 Spring Boot 运行配置中，为使用 MyBatis-Plus 的商城服务加入以下 JVM 参数：

```text
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED
```

覆盖的服务为：

- `ItemServiceApplication`
- `CartServiceApplication`
- `UserServiceApplication`
- `TradeServiceApplication`
- `PayServiceApplication`

`HmGatewayApplication` 不使用 MyBatis-Plus，不增加该参数。`marketing-service` 继续使用 Java 17，且本次不改动。Nginx 配置与静态文件不改动。

## 配置落点

修改当前项目的 IDEA 本地运行配置，使上述五个 Spring Boot 配置保存 `VM_PARAMETERS`。该配置只影响从本项目 IDEA 运行配置启动的本地进程，不影响 Docker 镜像、生产部署和 Maven 依赖。

## 重启与验证

配置保存后完全停止并重新启动 `UserServiceApplication`，确保之前初始化失败的 JVM 被替换。验证顺序如下：

1. `POST http://127.0.0.1:8083/users/login`，确认 user-service 直连登录成功。
2. `POST http://127.0.0.1:8080/users/login`，确认 Gateway 转发成功。
3. `POST http://127.0.0.1:18080/api/users/login`，确认本地 Nginx 完整链路成功。
4. 使用 `Jack / 123` 在浏览器登录页完成登录。

若无法由命令行可靠重启 IDEA 管理的进程，则保存配置后提示用户在 IDEA 中停止并重新启动对应应用，再继续执行三层验证。

## 后续处理

后续统一升级 MyBatis-Plus 到支持 JDK 17 的版本并完成回归验证后，删除临时的 `--add-opens` 参数。本次不提前进行依赖升级。
