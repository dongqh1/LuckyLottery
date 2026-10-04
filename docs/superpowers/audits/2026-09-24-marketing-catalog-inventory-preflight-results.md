# Marketing 商品与渠道库存只读预检结果

审计状态：未运行
SQL 执行日期：未采集
MySQL 服务端版本：未采集

未运行原因：尚无经确认仅具有 `SELECT` 权限的只读审计凭证；`luckyhub`、`hmall` 与 `information_schema` 的可读性未验证，因此未连接数据库或执行 SQL。

## 聚合结果

| 指标 | 结果 |
|---|---|
| `product` 状态分布 | 未采集 |
| `product_sku` 状态分布 | 未采集 |
| `product_sku` 销售模式分布 | 未采集 |
| `product_sku` 直接关联 HMall item 的字段数 | 未采集 |
| `inventory_ledger` 身份形态分布 | 未采集 |
| 缺失 Marketing SKU 的 ledger 数量 | 未采集 |
| 缺失 HMall item 的 ledger 数量 | 未采集 |
| `inventory_reservation` 状态分布 | 未采集 |
| SKU 库存分配量超过总量的记录数 | 未采集 |
| 渠道库存账面等式不成立的记录数 | 未采集 |
| `hmall.item` 总数 | 未采集 |

## 未决事项

- 取得经确认仅有 `SELECT` 权限、并可读 `luckyhub`、`hmall` 与 `information_schema` 的审计凭证后，方可执行只读预检并回填上述聚合结果。
- Marketing SKU 与 `hmall.item` 的对应关系尚未核实；不得依据 ID、商品名称或 SKU 编码推断映射。若发现映射歧义或库存异常，应记录并暂停后续迁移。
