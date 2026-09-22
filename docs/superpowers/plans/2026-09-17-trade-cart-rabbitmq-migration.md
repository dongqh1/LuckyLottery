# Trade Cart Cleanup RabbitMQ Migration Plan

## Scope

Replace only the synchronous cart cleanup performed after a trade order is created. Keep address, catalog, stock, coupon, membership and payment calls synchronous because their results affect the current request.

## Design

- Persist an `ORDER_CREATED` message in a trade outbox in the same transaction as the order.
- Relay due outbox rows to durable topic exchange `trade.events.topic` with routing key `trade.order.created` and publisher confirms.
- Consume from durable queue `cart.trade.order-created.queue` in cart-service.
- Retry listener failures three times, then dead-letter to `cart.trade.order-created.dlq` through `trade.events.dlx`.
- Delete only cart rows owned by the event user whose `update_time` is not later than the event timestamp, making redelivery idempotent and preserving items re-added after checkout.

## Verification

- Unit-test order creation appends the event and no longer calls CartClient.
- Unit-test publisher confirms/returns and outbox retry state transitions.
- Unit-test listener delegation and cutoff-aware cart deletion.
- Compile affected modules and run their focused regression suites.
- Verify the topology against the real `/dongqh` RabbitMQ vhost.
