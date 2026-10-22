# Pure Logistics Service Implementation Plan

## Goal
Convert fulfillment-service into the project's logistics-only business service while preserving Outbox + RabbitMQ, points redemption shipping, frozen order/address behavior, and prior lottery simplifications.

## Scope
- Lottery coupon, points, and membership rewards call their owning services through existing idempotent APIs; physical prizes remain claimable into logistics.
- Rename runtime service identity and client contracts to logistics-service while preserving module/source package paths and existing database compatibility where practical.
- On paid shop orders, publish a trade Outbox event through RabbitMQ; logistics fetches frozen delivery details from Trade and creates an idempotent shipping order.
- Admins can update carrier, waybill number, and simulated tracking events from a new admin page.
- Preserve points-redemption shipping and existing order/address snapshots.
- Do not modify marketing-service.

## Tasks
1. Add tests and change virtual lottery reward dispatch to direct idempotent Coupon, Points, and Membership APIs; retain MQ/Outbox for reward processing and physical shipping.
2. Establish logistics client/service identity and update Lottery, Points, Gateway, Compose and internal endpoints; retain points-redemption and lottery-claim shipping.
3. Add paid trade-order event publication via the existing Trade Outbox/RabbitMQ flow, provide an internal Trade endpoint for frozen shipping details, and consume idempotently in logistics.
4. Add admin-authorized carrier/waybill/tracking-event maintenance APIs and a logistics admin UI; show tracking details to users where existing order/benefit pages support them.
5. Run focused unit/contract tests and frontend checks; inspect the scoped diff for accidental edits outside this feature.

## Constraints
- Keep all existing shop order price/address snapshots and logistics address snapshots.
- Keep database migrations backward-compatible with existing local data and compose startup.
- Preserve idempotency, retry, and outbox guarantees.
- Avoid changing unrelated uncommitted lottery work.
