# 用户、交易、支付数据库拆分设计

## 目标

仅完成用户、交易、支付三个领域的数据库物理隔离，以及对应 Spring 配置中的数据库连接信息。Java 业务代码、Maven 依赖、`hm-api` 和 OpenFeign 调用不在本次范围内。

## 拆分依据

以 `mysql/sqls` 中黑马商城提供的拆分 SQL 决定表的领域归属，以当前 `hmall` 数据库中的实际表结构和数据作为迁移源。这样既遵循教程的边界，也保留项目融合后新增的会员折扣、优惠券、订单金额和物流快照等字段。

## 目标数据库结构

### `hm_user`

- `user`
- `address`

账号为 `hm_user_app`，密码为 `hmall-lottery-user-dev`。

### `hm_trade`

- `order`
- `order_detail`
- `order_logistics`

账号为 `hm_trade_app`，密码为 `hmall-lottery-trade-dev`。

### `hm_pay`

- `pay_order`

账号为 `hm_pay_app`，密码为 `hmall-lottery-pay-dev`。

每个账号只获得所属数据库的 `SELECT`、`INSERT`、`UPDATE`、`DELETE` 权限，不授予其他业务库权限。

## 数据迁移

新增三个按顺序编号的、可重复执行的迁移 SQL：

1. 创建目标数据库和服务账号。
2. 使用 `CREATE TABLE ... LIKE hmall.<table>` 复制当前实际表结构。
3. 使用 `INSERT IGNORE ... SELECT ... FROM hmall.<table>` 复制现有数据，仅补充目标库缺失的主键记录。
4. 不删除或修改原 `hmall` 中的源表，保留其作为回滚与核对来源。

选择 `LIKE` 当前源表而不是直接执行官方数据文件，是因为官方 SQL 中含旧版字段、示例数据和 `DELETE` 语句，直接导入可能遗漏融合后的结构或覆盖现有数据。

迁移脚本接入根目录 `compose.yaml` 的 `schema-init` 服务，使已有 Docker 数据卷也能执行拆分，而不是只在首次创建 MySQL 容器时生效。

## YAML 配置

只调整数据库连接相关 YAML：

- `user-service` 指向 `hm_user`。
- `trade-service` 指向 `hm_trade`。
- `pay-service` 指向 `hm_pay`。
- `local` 配置连接 `127.0.0.1:13307`。
- `dev` 配置连接 Docker 网络中的 `mysql:3306`。
- 数据库用户名和密码按用户要求直接写在相应 YAML 中。

根 `compose.yaml` 的 `schema-init` 同步使用相同的三个账号密码，以创建或更新 MySQL 用户。除数据库连接配置外，不调整服务的其他配置。

## 验证标准

完成迁移后验证：

1. `hm_user`、`hm_trade`、`hm_pay` 均已创建。
2. 每个目标库的表集合与本设计一致。
3. 目标表字段与 `hmall` 对应源表一致。
4. 目标表数据行数与源表一致；重复执行迁移不会重复插入数据。
5. 三个服务账号能够连接并读写自己的数据库。
6. 三个服务账号不能访问另外两个新业务数据库。
7. 原 `hmall` 数据库和其中的源表仍然存在。
8. `docker compose config` 能正确解析更新后的 Compose 配置。

## 明确不做

- 不修改任何 Java 文件。
- 不调整 Maven 聚合或依赖。
- 不创建或修改 OpenFeign 客户端。
- 不移除单体数据库中的原表。
- 不处理跨服务事务或业务调用。
