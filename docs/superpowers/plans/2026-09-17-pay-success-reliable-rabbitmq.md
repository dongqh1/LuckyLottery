# Pay Success Reliable RabbitMQ Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reliably publish payment success through a transactional Outbox and independently update Trade and consume the locked coupon in Marketing.

**Architecture:** Pay writes a versioned `PaySuccessMessage` and `pay_message_outbox` row in the payment transaction. A lease-based relay publishes with correlated confirms to `pay.direct/pay.success`; Trade and Marketing consume from separate durable queues with idempotent handlers and separate DLQs.

**Tech Stack:** Java 17, Spring Boot 4.0.8, Spring AMQP 4, MyBatis-Plus, MySQL 8, JUnit 5, Mockito, Python contract tests.

## Global Constraints

- Preserve exchange `pay.direct` and routing key `pay.success`.
- Use virtual host `/dongqh` and the existing `.env`; do not add Nacos shared configuration.
- Keep payment validation, balance deduction, and payment-row state change synchronous.
- Do not modify the rollback-only `mall-backend/hm-service` flow.
- Use `JacksonJsonMessageConverter` from Spring AMQP 4.
- Stop after this migration is verified; do not begin fulfillment migration.

---

### Task 1: Version the shared payment-success event

**Files:**
- Modify: `hm-api/src/main/java/com/dongqh/hmapi/dto/PaySuccessMessage.java`
- Modify: `trade-service/src/test/java/com/dongqh/tradeservice/listener/PayStatusListenerTest.java`
- Create: `tests/test_pay_success_reliable_rabbitmq_contract.py`

**Interfaces:**
- Produces: `PaySuccessMessage(String eventId, Long orderId, Long userId, Integer amount, Long couponId, LocalDateTime paidAt)`.

- [ ] Add a source contract requiring all six fields and forbidding `rabbitTemplate.convertAndSend` and `couponCheckoutClient.use` in `PayOrderServiceImpl`.
- [ ] Update `PayStatusListenerTest` to construct the six-field event.
- [ ] Run `python -m pytest tests/test_pay_success_reliable_rabbitmq_contract.py -q` and the focused listener test; confirm failures are caused by the old three-field record and direct sender.
- [ ] Extend the record with the six exact fields and validations for required identifiers, positive amount, and non-null `paidAt`.
- [ ] Re-run the contract test for the event portion and compile `hm-api`.

### Task 2: Add the Pay transactional Outbox and reliable publisher

**Files:**
- Create: `mysql/migrations/V010__add_pay_message_outbox.sql`
- Create: `pay-service/src/main/java/com/dongqh/payservice/messaging/PayMessageOutbox.java`
- Create: `pay-service/src/main/java/com/dongqh/payservice/messaging/PayOutboxStatus.java`
- Create: `pay-service/src/main/java/com/dongqh/payservice/messaging/PayMessageOutboxMapper.java`
- Create: `pay-service/src/main/java/com/dongqh/payservice/messaging/PayOutboxService.java`
- Create: `pay-service/src/main/java/com/dongqh/payservice/messaging/PaySuccessPublisher.java`
- Create: `pay-service/src/main/java/com/dongqh/payservice/messaging/PayOutboxRelay.java`
- Create: `pay-service/src/main/java/com/dongqh/payservice/messaging/PayMessaging.java`
- Create: `pay-service/src/main/java/com/dongqh/payservice/config/PayMessagingConfiguration.java`
- Modify: `pay-service/src/main/resources/application.yaml`
- Test: `pay-service/src/test/java/com/dongqh/payservice/messaging/PayOutboxServiceTest.java`
- Test: `pay-service/src/test/java/com/dongqh/payservice/messaging/PaySuccessPublisherTest.java`
- Test: `pay-service/src/test/java/com/dongqh/payservice/messaging/PayOutboxRelayTest.java`

**Interfaces:**
- `PayOutboxService.append(PaySuccessMessage)` requires an active transaction and inserts `PENDING`.
- `PaySuccessPublisher.publish(PaySuccessMessage)` returns only after ACK and rejects returned/NACK/timed-out messages.
- `PayOutboxRelay.relay()` claims rows with a lease and marks `SENT` only after `publish` returns.

- [ ] Write focused tests for serialization/insertion, publisher ACK/NACK, relay success/failure, and topology declaration.
- [ ] Run the focused tests and confirm missing classes fail compilation.
- [ ] Add the idempotent `pay_message_outbox` schema with unique `event_id` and relay index.
- [ ] Implement the entity, status enum, mapper SQL, transaction-mandatory append service, correlated-confirm publisher, and lease relay following the existing Trade Outbox pattern.
- [ ] Declare durable `pay.direct`; enable correlated confirms, returns, mandatory routing, and `hm.messaging` relay settings in Pay YAML.
- [ ] Run all Pay messaging tests and compile Pay.

### Task 3: Replace direct payment side effects with Outbox append

**Files:**
- Modify: `pay-service/src/main/java/com/dongqh/payservice/service/impl/PayOrderServiceImpl.java`
- Create: `pay-service/src/test/java/com/dongqh/payservice/service/impl/PaySuccessOutboxTest.java`

**Interfaces:**
- Consumes: `PayOutboxService.append(PaySuccessMessage)`.
- Produces exactly one event with UUID event ID, authoritative order/user/amount/coupon snapshots, and the same `paidAt` used by the payment row.

- [ ] Write a focused service test proving successful payment appends the event and never invokes the coupon HTTP client or RabbitTemplate.
- [ ] Run the test and source contract; confirm they fail against the direct sender.
- [ ] Replace `RabbitTemplate` and `CouponCheckoutClient` dependencies with `PayOutboxService` for this flow and append after the payment CAS within the transaction.
- [ ] Run the focused Pay tests and source contract until green.

### Task 4: Make the Trade consumer idempotent and dead-lettered

**Files:**
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/listener/PayStatusListener.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/service/IOrderService.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/service/impl/OrderServiceImpl.java`
- Create: `trade-service/src/main/java/com/dongqh/tradeservice/config/PayMessagingConfiguration.java`
- Modify: `trade-service/src/test/java/com/dongqh/tradeservice/listener/PayStatusListenerTest.java`
- Create: `trade-service/src/test/java/com/dongqh/tradeservice/service/impl/PaySuccessIdempotencyTest.java`

**Interfaces:**
- `markOrderPaySuccess(Long orderId, Long userId, Integer amount, LocalDateTime paidAt)` succeeds for a valid first delivery and a matching duplicate.

- [ ] Write tests for first delivery, matching duplicate, amount mismatch, user mismatch, and invalid state.
- [ ] Run focused tests and confirm duplicate/idempotency expectations fail.
- [ ] Pass `paidAt` from the listener and implement locked read plus conditional idempotent transition.
- [ ] Configure `trade.pay.success.queue` with `pay.dlx` and `trade.pay.success.dlq`.
- [ ] Re-run Trade tests and compile Trade.

### Task 5: Add the Marketing coupon consumer and DLQ

**Files:**
- Modify: `marketing-service/pom.xml`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/coupon/messaging/PaySuccessCouponListener.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/coupon/config/PayCouponMessagingConfiguration.java`
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/coupon/messaging/PaySuccessCouponListenerTests.java`
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/coupon/config/PayCouponMessagingConfigurationTests.java`

**Interfaces:**
- Consumes `PaySuccessMessage`; if `couponId != null`, calls `CouponCheckoutService.use(userId, couponId, orderId.toString())`.

- [ ] Write tests for coupon consumption, null-coupon skip, exception propagation, and complete Rabbit topology.
- [ ] Run focused tests and confirm missing listener/configuration fail compilation.
- [ ] Add the `hm-api` dependency, listener, durable `marketing.pay.coupon-use.queue`, DLX, and `marketing.pay.coupon-use.dlq`.
- [ ] Re-run focused Marketing tests and compile Marketing.

### Task 6: Cross-module verification and local RabbitMQ probe

**Files:**
- Modify if required by verification only: `tests/test_pay_success_reliable_rabbitmq_contract.py`

- [ ] Run `python -m pytest tests/test_pay_success_reliable_rabbitmq_contract.py tests/test_pay_success_rabbitmq_contract.py tests/test_rabbitmq_infrastructure_contract.py -q`.
- [ ] Run Maven tests for `hm-api`, `pay-service`, `trade-service`, and focused Marketing messaging/coupon tests using JDK 17.
- [ ] Run the repository infrastructure/schema contract tests covering `V010`.
- [ ] If the local RabbitMQ stack is available, declare/inspect `pay.direct`, both queues, `pay.dlx`, and both DLQs; publish one probe and clear it afterward.
- [ ] Inspect `git diff` to ensure only this migration's files changed in this phase, then report exact test evidence and pause.
