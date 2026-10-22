# Lottery 抽奖与发奖快照移除设计

## 目标

从 `lottery-service` 移除抽奖和发奖过程中冻结并重复保存的奖品/奖励快照，让抽奖历史和异步发奖按奖品 ID 读取当前奖品及奖励定义。保留 Outbox、RabbitMQ、抽奖库存扣减、抽奖订单与幂等行为。

## 范围

移除：

- 奖励定义 `config_snapshot` JSON 字段，以及创建 DTO、管理 VO 和营销管理页上的“奖励配置 JSON”。
- `lottery_draw_record` 中复制的奖品名称、类型、图片及奖励定义、类型、目标、数量、payload、fingerprint。
- `user_benefit` 中复制的奖品类型和奖励定义、类型、目标、数量、payload、fingerprint。
- `RewardSnapshot`、奖励 fingerprint 校验和依赖这些冻结字段的发奖、实物领取、权益过期回补、履约状态投影逻辑。
- Outbox 奖励事件中的快照类型/指纹字段。新事件只保留 `benefitId`、`drawRecordId`、`prizeId` 等稳定业务 ID。
- 旧待处理 Outbox 奖励事件 JSON 中的奖励快照属性；消费者仍能读取其余身份字段。

保留：

- `marketing_prize.reward_definition_id`，它是奖品到奖励定义的当前配置关系，不是中奖时复制出的快照。
- 抽奖订单、抽奖记录、中奖权益及活动奖品库存记录。抽奖记录继续保存 `prizeId`、用户、活动、结果、时间等必要业务身份。
- `EligibilitySnapshot`、`DrawPrizeSnapshot`、`PrizeWeightSnapshot` 的一次请求内计算数据语义，但将其重命名为当前资格/奖品候选模型；抽奖仍须读取当下权重、剩余库存和启用状态才能执行随机算法。
- `message_outbox` 投递、RabbitMQ 消费幂等、库存原子扣减及奖励发放状态机。
- 订单计价快照、会员购买支付快照、履约收货地址快照，以及其它服务的快照功能。

## 行为设计

1. `DrawEligibilityService` 读取当前活动奖池和奖品数据，验证当前奖励定义及商品/优惠券/会员目标有效；运行时数据改用 `DrawEligibility`、`DrawPrizeCandidate`、`WeightedPrize` 等非快照命名类型。
2. 抽奖事务只记录中奖的 `prizeId` 和必要的订单/用户/活动身份，不复制奖品展示字段和奖励配置。查询抽奖历史、抽奖结果与用户权益时，以 `prizeId` LEFT JOIN 当前 `marketing_prize` 和 `reward_definition`，API 保持原有展示字段，名称、类型、图片和商品 ID 由当前数据填充。
3. Outbox `PRIZE_FULFILLMENT_REQUESTED` 新消息只含 `benefitId`、`drawRecordId`、`prizeId`。消费者先按现有订单/记录/权益 ID 校验归属和状态，再由 `prizeId -> marketing_prize.reward_definition_id -> reward_definition` 解析当前奖励配置，并按需查询 item、coupon、membership 服务的当前目标数据后创建履约任务或发放奖励。
4. 实物领取按当前奖品/奖励关系解析 PRODUCT 目标，并在创建物流单前检查商品当前库存不少于奖励数量；履约状态投影根据当前奖励类型判断状态迁移。领取过期时根据当前奖品类型和抽奖记录中的活动/奖品 ID 回补活动奖品库存，不依赖商品服务可用。缺少或禁用的当前配置继续进入现有隔离/失败处理，不伪造奖励。
5. 旧 Outbox JSON 的冻结字段在 V4 迁移中移除；事件反序列化允许忽略遗留额外字段，避免升级前已存在的待投递消息无法消费。

## 数据库迁移

新增 `V4__remove_lottery_reward_snapshots.sql`，不修改已经可能执行过的 V1/V2/V3：

- 从 `reward_definition` 删除 `config_snapshot`。
- 从 `lottery_draw_record` 删除 `prize_name`、`prize_type`、`prize_image_url` 和 `reward_definition_id`、`reward_type`、`reward_target_id`、`reward_quantity`、`reward_payload`、`reward_fingerprint` 及其相关索引/约束。
- 从 `user_benefit` 删除 `prize_type` 和上述奖励复制字段及相关索引/约束。
- 对 `PRIZE_FULFILLMENT_REQUESTED` 旧 Outbox JSON 使用 `JSON_REMOVE` 清理嵌套 `payload` 中的 `prizeType`、`rewardDefinitionId`、`rewardType`、`rewardFingerprint`。

迁移会丢弃既有中奖记录中复制保存的历史名称/图片/奖励 payload；迁移后历史页面从当前奖品目录显示信息。保留 `prizeId`、权益 ID、履约单号和状态等业务数据。

## 主要风险

- 消息积压期间如果管理员更换了奖品关联或外部目标信息，后续发放会按消费时的当前配置执行，而不是中奖时的值。当前目标无效时按隔离/失败路径处理。
- 历史中奖名称、类型、图片会反映当前奖品资料；无法再从中奖行还原修改前的展示内容。
- 实物领取对商品服务库存的读取是创建物流单前的可用性检查，不是原子预占；活动奖品库存与商品服务库存仍是两个资源。
- 当前活动资格仍在请求开始时读取一份内存数据供随机算法使用；这是计算输入，不落库也不作为跨请求奖励依据。

## 验收标准

- 抽奖服务生产代码、API DTO、Outbox 消息和管理页面不再包含持久化奖励快照/fingerprint/config snapshot 路径。
- 抽奖和用户权益历史可由当前奖品 ID 查询；中奖事务、请求幂等和库存扣减仍正确。
- 新 Outbox 消息仅含必要身份 ID；旧消息能被消费并使用当前奖励定义。
- 当前奖励分别能处理 PRODUCT、COUPON、POINTS、MEMBERSHIP、DRAW_CHANCE；失效目标进入隔离/失败处理。
- 实物领取、领取超期回补、履约状态映射在不依赖冻结字段时仍通过测试。
- 订单和地址快照相关服务文件没有因本次任务被修改。
