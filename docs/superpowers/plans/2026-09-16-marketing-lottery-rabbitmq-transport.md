# Marketing Lottery RabbitMQ Transport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the marketing lottery Redis Stream event transport with reliable RabbitMQ publishing and consumption while preserving the transactional Outbox and idempotent business consumer.

**Architecture:** Keep `DrawEventPublisher`, `DrawEventEnvelope`, `OutboxRelayService`, and `MessageConsumeService` as transport-independent boundaries. Add a Spring AMQP publisher, listener, JSON converter, and durable exchange/queue/binding declarations; use broker confirms on publish and finite listener retries followed by dead-lettering.

**Tech Stack:** Java 17, Spring Boot 4.0.8, Spring AMQP, RabbitMQ 4.3.5, Jackson 3, JUnit 5, AssertJ, Mockito, Maven.

## Global Constraints

- Migrate only asynchronous lottery events in `marketing-service` during this checkpoint.
- Keep synchronous HTTP/Feign calls unchanged.
- Keep Redis for quotas, reservations, caching, and distributed locking.
- Use RabbitMQ virtual host `/dongqh` and credentials from `.env`; do not move them into Nacos.
- Stop after verification and wait for explicit user confirmation before migrating another subsystem.

---

### Task 1: Add the AMQP dependency and define topology contracts

**Files:**
- Modify: `marketing-service/pom.xml`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/lottery/messaging/rabbit/LotteryRabbitTopology.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/lottery/config/LotteryMessagingConfiguration.java`
- Test: `marketing-service/src/test/java/com/dongqh/luckyhub/lottery/messaging/RabbitTopologyConfigurationTests.java`

**Interfaces:**
- Produces constants for exchange, queue, dead-letter resources, and the three routing keys.
- Produces durable `TopicExchange`, `Queue`, and `Binding` beans plus a Boot 4 `JacksonJsonMessageConverter`.

- [ ] **Step 1: Write a failing topology test**

Assert that the configuration exposes `lottery.events.topic`, `lottery.events.queue`, `lottery.events.dlx`, and `lottery.events.dlq`; verify durability, the primary queue's `x-dead-letter-exchange`, exact primary bindings, wildcard dead-letter binding, and `JacksonJsonMessageConverter` type.

- [ ] **Step 2: Run the topology test and verify RED**

Run: `mvn -pl marketing-service -Dtest=RabbitTopologyConfigurationTests test`

Expected: test compilation fails because `LotteryRabbitTopology` and the Rabbit configuration methods do not exist.

- [ ] **Step 3: Add the dependency and minimal declarations**

Add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

Declare durable exchanges/queues and bindings with `QueueBuilder.durable(...).deadLetterExchange(...)`, and return `new JacksonJsonMessageConverter()` from a `MessageConverter` bean.

- [ ] **Step 4: Run the topology test and verify GREEN**

Run: `mvn -pl marketing-service -Dtest=RabbitTopologyConfigurationTests test`

Expected: `BUILD SUCCESS`, one test class with no failures.

### Task 2: Add confirmed RabbitMQ publishing

**Files:**
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/lottery/messaging/rabbit/RabbitDrawEventPublisher.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/lottery/config/MessagingProperties.java`
- Test: `marketing-service/src/test/java/com/dongqh/luckyhub/lottery/messaging/RabbitDrawEventPublisherTests.java`

**Interfaces:**
- Consumes `RabbitTemplate`, `MessagingProperties.publisherConfirmTimeout()`, and `DrawEventEnvelope`.
- Produces `void publish(DrawEventEnvelope)` through the existing `DrawEventPublisher` interface.

- [ ] **Step 1: Write failing publisher tests**

Cover each `DrawEventType` routing key. Complete `CorrelationData.getFuture()` with ACK or NACK in the mocked template. Verify ACK succeeds, NACK throws, a populated returned message throws, and confirm timeout throws.

- [ ] **Step 2: Run the publisher test and verify RED**

Run: `mvn -pl marketing-service -Dtest=RabbitDrawEventPublisherTests test`

Expected: test compilation fails because `RabbitDrawEventPublisher` does not exist.

- [ ] **Step 3: Implement the minimal publisher**

Send with:

```java
CorrelationData correlation = new CorrelationData(event.eventId().toString());
rabbitTemplate.convertAndSend(
        LotteryRabbitTopology.EVENT_EXCHANGE,
        LotteryRabbitTopology.routingKey(event.eventType()),
        event,
        correlation);
CorrelationData.Confirm confirm = correlation.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
```

Reject a NACK or `correlation.getReturned() != null`, preserve interruption status, and wrap checked timeout/execution failures as an AMQP publish failure.

- [ ] **Step 4: Run the publisher test and verify GREEN**

Run: `mvn -pl marketing-service -Dtest=RabbitDrawEventPublisherTests test`

Expected: `BUILD SUCCESS` with all publisher cases passing.

### Task 3: Add listener delegation, retry, and dead-letter behavior

**Files:**
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/lottery/messaging/rabbit/RabbitDrawEventListener.java`
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/lottery/messaging/RabbitDrawEventListenerTests.java`
- Modify: `marketing-service/src/main/resources/application.yaml`
- Modify: `marketing-service/src/test/resources/application.properties`

**Interfaces:**
- Consumes `DrawEventEnvelope` from `lottery.events.queue`.
- Delegates to `MessageConsumeService.consume(DrawEventEnvelope)` and lets failures propagate.

- [ ] **Step 1: Write failing listener tests**

Verify the listener passes the exact envelope to `MessageConsumeService`; verify a business exception propagates so the container can retry/reject it.

- [ ] **Step 2: Run the listener test and verify RED**

Run: `mvn -pl marketing-service -Dtest=RabbitDrawEventListenerTests test`

Expected: test compilation fails because `RabbitDrawEventListener` does not exist.

- [ ] **Step 3: Implement listener and YAML policy**

Use `@RabbitListener(queues = LotteryRabbitTopology.EVENT_QUEUE)` and call `consumeService.consume(event)`. Configure correlated confirms, publisher returns, mandatory publishing, three listener attempts, finite exponential intervals, and `default-requeue-rejected: false`.

- [ ] **Step 4: Run the listener test and verify GREEN**

Run: `mvn -pl marketing-service -Dtest=RabbitDrawEventListenerTests test`

Expected: `BUILD SUCCESS` with both listener behaviors passing.

### Task 4: Remove Redis Stream transport and adapt transport-level tests

**Files:**
- Delete: `marketing-service/src/main/java/com/dongqh/luckyhub/lottery/messaging/redis/RedisStreamDrawEventPublisher.java`
- Delete: `marketing-service/src/main/java/com/dongqh/luckyhub/lottery/messaging/redis/RedisStreamDrawEventConsumer.java`
- Delete: `marketing-service/src/main/java/com/dongqh/luckyhub/lottery/messaging/redis/RedisStreamInitializer.java`
- Delete: `marketing-service/src/test/java/com/dongqh/luckyhub/lottery/messaging/RedisStreamMessagingTests.java`
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/lottery/messaging/OutboxMessagingTests.java`
- Modify: affected tests that construct `MessagingProperties` or directly deliver through Redis Stream.
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/lottery/service/MessageConsumeService.java`

**Interfaces:**
- Keeps Outbox and business-consumer tests transport-independent.
- Representative end-to-end tests deliver captured Outbox envelopes through `RabbitDrawEventListener.onMessage` rather than Redis polling internals.

- [ ] **Step 1: Update tests to the target contract and verify RED**

Replace Redis Stream setup/assertions with captured `DrawEventPublisher` calls and direct listener delegation. Update configuration tests to assert `provider`, stream/group, polling, and claim properties are absent and confirm timeout is present.

- [ ] **Step 2: Run affected tests and confirm they fail against the old properties/classes**

Run: `mvn -pl marketing-service -Dtest=LotteryConfigurationTests,OutboxMessagingTests,LotteryEndToEndTests,LotteryFiveRewardEndToEndTests,LotteryRewardDispatchTests,MessageConsumeServiceTests,BenefitFulfillmentServiceTests test`

Expected: failures reference the old `MessagingProperties` shape or missing Rabbit delivery behavior.

- [ ] **Step 3: Remove Redis transport code and Redis-specific properties**

Reduce `MessagingProperties` to:

```java
public record MessagingProperties(
        boolean enabled,
        String logicalConsumerName,
        Duration publisherConfirmTimeout,
        Duration outboxLease) { }
```

Retain validation for nonblank consumer name and positive durations. Update the stale Stream wording in `MessageConsumeService` documentation to broker-neutral redelivery wording.

- [ ] **Step 4: Run the affected tests and verify GREEN**

Run the command from Step 2.

Expected: `BUILD SUCCESS`, zero failures and zero errors.

### Task 5: Verify module, broker topology, and migration boundary

**Files:**
- Inspect only: all modified files and the Git diff.

**Interfaces:**
- Produces verification evidence and the user-facing checkpoint report; makes no second migration.

- [ ] **Step 1: Run all focused messaging/configuration tests**

Run: `mvn -pl marketing-service -Dtest=RabbitTopologyConfigurationTests,RabbitDrawEventPublisherTests,RabbitDrawEventListenerTests,OutboxMessagingTests,LotteryConfigurationTests test`

Expected: `BUILD SUCCESS` with zero failures/errors.

- [ ] **Step 2: Compile the complete marketing module**

Run: `mvn -pl marketing-service -DskipTests compile`

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run the complete marketing test suite when local dependencies are available**

Run: `mvn -pl marketing-service test`

Expected: `BUILD SUCCESS`; otherwise report exact external dependency failures separately and do not claim full-suite success.

- [ ] **Step 4: Verify RabbitMQ when the container is available**

Inspect `docker compose ps rabbitmq`, verify vhost `/dongqh`, start the service/test context that declares beans, then list exchanges, queues, and bindings. Verify a real envelope can be published and consumed without leaving a message in the primary queue.

- [ ] **Step 5: Audit scope and stop**

Run `git diff -- marketing-service` and `rg -n "RedisStream|redis-stream|lottery-stream|lottery-group" marketing-service`.

Expected: no Redis Stream transport references remain; Redis quota/cache/lock code remains. Report each changed file, old-to-new behavior, topology creation, credentials source, and every test result. Pause for explicit user confirmation.
