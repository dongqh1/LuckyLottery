# Service Package Root Unification

## Goal

Give each business service one stable Java package root based on its module's business name. This removes the current split between names such as `com.dongqh.couponservice` and `com.dongqh.luckyhub`, and makes package declarations and imports follow the module boundary.

## Package mapping

| Module | Package root |
| --- | --- |
| `cart-service` | `com.dongqh.cart` |
| `coupon-service` | `com.dongqh.coupon` |
| `fulfillment-service` | `com.dongqh.fulfillment` |
| `item-service` | `com.dongqh.item` |
| `lottery-service` | `com.dongqh.lottery` |
| `marketing-service` | `com.dongqh.marketing` |
| `membership-service` | `com.dongqh.membership` |
| `pay-service` | `com.dongqh.pay` |
| `points-service` | `com.dongqh.points` |
| `trade-service` | `com.dongqh.trade` |
| `user-service` | `com.dongqh.user` |

Existing subpackage layers such as `controller`, `service`, `domain`, `mapper`, `config`, and `messaging` remain under each module's new root.

## Changes in scope

- Update package declarations and imports in each listed service's production and existing test Java sources.
- Move Java files so their source paths match their declared packages.
- Update explicit Spring component, Feign, and mapper scan package names and other package-based configuration.
- Update repository-local references to the old service package names where they exist.
- Preserve current public HTTP routes, message contracts, persistence mappings, and business logic.
- Preserve the user's existing uncommitted coupon code edits while changing package/import lines as needed.

## Out of scope

- `hm-api`, `hm-gateway`, and `hm-security` keep their current package roots.
- The legacy `mall-backend` keeps its `com.hmall` packages.
- No service boundaries, APIs, database schemas, or business behavior change.
- No dependency or module restructuring.

## Validation

- Search the repository for remaining references to the old business-service package roots.
- Compile the affected Maven modules with tests skipped to catch package, import, and source-path errors.
- Do not alter business behavior as part of this package migration.
