# HMall Lottery 微服务架构

## 1. 服务边界

商城服务由 item-service、cart-service、user-service、trade-service 和 pay-service 组成。营销与履约能力分别由 coupon-service、points-service、membership-service、lottery-service 和 fulfillment-service 提供；旧的整合式营销实现已移除。

hm-gateway 是 API 入口，hm-api 存放服务契约，hm-security 提供安全组件。mall-backend/hm-common 是共享库；旧 mall-backend/hm-service 只保留为可选回滚诊断模块。

## 2. 请求路由

- /items/**、/search/** → item-service
- /carts/** → cart-service
- /addresses/**、/users/** → user-service
- /orders/** → trade-service
- /pay-orders/** → pay-service
- /api/coupons/** → coupon-service
- /api/points/** → points-service
- /api/memberships/** → membership-service
- /api/lottery/**、/api/benefits/** → lottery-service
- /api/shipping/** → fulfillment-service

Nginx 保留 /api/marketing/** 兼容路径，并将它改写到网关的 /api/**；业务请求最终由网关路由到对应拆分服务。浏览器不直接访问容器内部地址。

## 3. 数据与迁移

商城业务库由 mysql/migrations 初始化。营销和履约服务各自管理数据库连接及 Flyway 迁移。marketing-data-migration 是一次性数据迁移任务，执行 V014__migrate_marketing_domain_data.sql，将旧业务数据迁入拆分后的服务数据库；它不是常驻业务微服务。

## 4. 本地运行

Compose 的 marketing-extraction profile 启动抽奖、积分、优惠券、会员和物流等拆分后的领域服务。完整栈还包含商城服务、网关、Nacos、MySQL、Redis、RabbitMQ、Seata 和 Nginx。旧商城单体仅通过 rollback profile 按需启动。
