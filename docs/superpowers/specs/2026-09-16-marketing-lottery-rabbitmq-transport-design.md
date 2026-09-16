# Marketing Lottery RabbitMQ Transport Design

## Scope

This checkpoint migrates only the asynchronous lottery event transport in `marketing-service` from Redis Stream to RabbitMQ. Synchronous HTTP/Feign calls remain synchronous. Redis remains in use for quotas, reservations, caching, and distributed locking.

The existing transactional Outbox, `DrawEventEnvelope`, and idempotent `MessageConsumeService` stay in place. No other scheduled worker or asynchronous subsystem is migrated in this checkpoint.

## Current Flow

`Lottery transaction -> message_outbox -> OutboxRelayService -> Redis Stream -> scheduled polling consumer -> MessageConsumeService`

Redis Stream currently adds three transport-specific responsibilities:

- `RedisStreamDrawEventPublisher` serializes and appends an envelope.
- `RedisStreamInitializer` creates the stream consumer group.
- `RedisStreamDrawEventConsumer` polls, claims stale pending records, dispatches, and acknowledges them.

## Target Flow

`Lottery transaction -> message_outbox -> OutboxRelayService -> RabbitMQ topic exchange -> durable queue -> @RabbitListener -> MessageConsumeService`

The Outbox row is marked `SENT` only after RabbitMQ positively confirms the publish. Consumer exceptions are retried three times by the listener container. Once retries are exhausted, the delivery is rejected and RabbitMQ routes it to the dead-letter exchange and queue.

## RabbitMQ Topology

- Durable topic exchange: `lottery.events.topic`
- Durable primary queue: `lottery.events.queue`
- Durable dead-letter exchange: `lottery.events.dlx`
- Durable dead-letter queue: `lottery.events.dlq`
- Primary routing keys:
  - `lottery.draw.confirmed`
  - `lottery.draw.release-requested`
  - `lottery.prize.fulfillment-requested`
- The primary queue declares `x-dead-letter-exchange=lottery.events.dlx`.
- The dead-letter queue binds to `lottery.events.dlx` with `#`, preserving all failed lottery event routing keys.

Spring AMQP `Exchange`, `Queue`, and `Binding` beans declare these resources idempotently through `RabbitAdmin` when the service connects to vhost `/dongqh`. They are not created through Nacos.

## Publishing Contract

`RabbitDrawEventPublisher` implements the existing `DrawEventPublisher` port. It maps `DrawEventType` to a fixed routing key, sends the complete `DrawEventEnvelope` as JSON, and attaches the event UUID as correlation data.

Publishing uses correlated publisher confirms and mandatory returns:

- broker ACK and no return: publish succeeds and the Outbox may become `SENT`;
- broker NACK, unroutable return, confirm timeout, interruption, or connection failure: publishing throws and the Outbox remains retryable through its existing failure/backoff logic.

The confirm timeout is configured as `luckyhub.messaging.publisher-confirm-timeout`.

## Consumption Contract

`RabbitDrawEventListener` receives a typed `DrawEventEnvelope` and delegates directly to `MessageConsumeService.consume`.

The listener uses Spring's automatic acknowledgement behavior: return successfully to ACK; throw to reject processing. Listener retry is enabled with three attempts and finite backoff. After exhaustion, `RejectAndDontRequeueRecoverer` rejects the message, allowing the queue's dead-letter exchange to capture it.

The existing `message_consume_record` uniqueness and quota transitions continue to make redelivery safe. No RabbitMQ delivery tag is persisted in the business database.

## Configuration

`marketing-service` adds `spring-boot-starter-amqp` and a Boot 4 `JacksonJsonMessageConverter`.

Rabbit connection details continue to come from the root `.env` through these existing variables:

- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `RABBITMQ_VHOST=/dongqh`

The Redis Stream provider, stream, group, polling, and claim-idle settings are removed. Redis connection configuration stays untouched.

## Testing and Checkpoint

Tests cover topology declarations, event-to-routing-key mapping, JSON message conversion, positive confirms, NACKs, unroutable returns, timeouts, listener delegation, Outbox retry behavior, and representative lottery flows.

After focused tests and `marketing-service` compilation, the implementation stops. If the local RabbitMQ container is available, broker vhost/topology and a real publish/consume round trip are also verified. No second migration begins until the user confirms this checkpoint.
