# 商品数据库渐进隔离设计

## 目标

在现有单 MySQL 8.4 实例内新增 `hm_item` 数据库，由 `item-service` 独占运行账号和数据写权限。迁移期间保留 `hmall.item`，避免订单、购物车仍在单体服务内直接访问商品表时中断。

## 本阶段边界

- 创建 `hm_item` 数据库和 `hm_item.item` 表。
- 首次及重复执行迁移时，以 `INSERT IGNORE` 从 `hmall.item` 补齐尚未迁移的商品。
- 创建 `hm_item_app` 运行账号，只授予 `hm_item.*` 的 `SELECT/INSERT/UPDATE/DELETE`。
- `item-service` 使用 `hm_item_app` 连接 `hm_item`。
- `schema-init` 继续使用 root 执行建库、建表、建账号和授权。
- 不删除或修改 `hmall.item`，不建立跨库外键，不改订单和购物车业务代码。

## 后续迁移

订单和购物车改为调用 `item-service` 后，停止对 `hmall.item` 的写入并核对数据，再单独设计旧表下线。营销服务继续只保存 HMall 商品业务 ID，并通过接口获取商品信息。

## 验证

- 基础设施契约测试验证 SQL、Compose 挂载、密码传递和商品服务连接配置。
- Maven 构建验证 `item-service` 配置仍能加载。
- 若 Docker MySQL 已运行，则执行幂等 `schema-init` 并核对数据库、账号授权和两张商品表的数据量。
