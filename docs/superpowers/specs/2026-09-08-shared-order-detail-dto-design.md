# OrderDetailDTO 跨服务统一设计

## 目标

解决 `trade-service` 调用 `ItemClient.deductStock()` 时，因为三个模块各自定义同名 `OrderDetailDTO` 而产生的 Java 泛型类型不兼容问题。本次只修复已确认的 DTO、商品 Feign 路径以及 `OrderServiceImpl` 中紧邻的残留引用，不处理支付服务拆分。

## 类型归属

`hm-api` 中的 `com.dongqh.hmapi.dto.OrderDetailDTO` 作为唯一跨服务订单商品参数，字段仅包含 `itemId` 和 `num`。它是普通传输对象，不是数据库 PO，不增加网络请求、序列化字段或运行时开销。

删除以下重复定义：

- `trade-service` 的本地 `OrderDetailDTO`
- `item-service` 的本地 `OrderDetailDTO`

trade 和 item 中所有相关调用统一导入 hm-api 类型，包括下单表单、库存接口、库存实现、Mapper 参数和现有营销调用。

## Feign 与服务端接口

`ItemClient.deductStock()` 使用 `List<com.dongqh.hmapi.dto.OrderDetailDTO>`，路径修正为完整 Controller 路径 `/items/stock/deduct`。item-service 的 Controller、Service、实现和 Mapper 同样接收共享 DTO。

## OrderServiceImpl 邻近修复

- 将残留的 `itemService.queryItemByIds()` 改为 `itemClient.queryItemByIds()`。
- 将 `buildLogistics(Long, Address)` 参数改为 `AddressDTO`。
- 不修改 `PayOrderMapper` 相关代码或支付调用。

## PayOrderMapper 边界

Mapper 不通过 HTTP 传输，也不放入 Feign Client。后续拆分支付逻辑时，`PayOrderMapper` 只留在 `pay-service`，trade-service 通过 `PayClient` 调用 pay-service 暴露的业务 HTTP 接口。本次仅记录边界，不实施该修改。

## 验证

- 静态检查确保项目中只有 hm-api 保留 `OrderDetailDTO` 定义。
- 验证 ItemClient 路径与 ItemController 完整路径一致。
- 编译 hm-api 与 item-service。
- 编译 trade-service 并区分本次修复与既有未拆分支付代码产生的错误。
- 不创建 Git 提交。
