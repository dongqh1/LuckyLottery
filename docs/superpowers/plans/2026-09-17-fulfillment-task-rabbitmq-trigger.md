# Fulfillment Task RabbitMQ Trigger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make RabbitMQ the primary trigger for newly created fulfillment tasks while preserving the database task state machine and reducing the scheduler to compensation work.

**Architecture:** A task creation or manual retry transaction writes a dedicated `fulfillment_outbox` row. A lease-based relay publishes `FulfillmentTaskReadyEvent` through correlated confirms to `fulfillment.direct/fulfillment.task.ready`; the queue consumer atomically claims that one database task, then executes the existing synchronous gateway/state transition flow outside the claim transaction. A grace-aware scheduler remains responsible only for missed pending notifications, due retries/reconciliation, and expired leases.

**Tech Stack:** Java 17, Spring Boot 4.0.8, Spring AMQP 4, MyBatis-Plus, MySQL 8/Flyway, JUnit 5, AssertJ, Mockito.

## Global Constraints

- Keep `fulfillment_task`, `fulfillment_attempt`, and `fulfillment_quarantine` as the authoritative state machine and audit data.
- Publish to durable direct exchange `fulfillment.direct` with routing key `fulfillment.task.ready`.
- Consume from `marketing.fulfillment.execute.queue`; dead-letter exhausted listener failures to `marketing.fulfillment.execute.dlq` through `fulfillment.dlx`.
- Keep coupon, points, membership, and logistics gateway calls synchronous and outside database transactions.
- Keep admin create, read, page, retry, and terminate HTTP contracts synchronous and unchanged.
- Do not modify post-success benefit projection; preserve the existing logistics projection call only.
- Use a dedicated `fulfillment_outbox`; do not generalize or change the lottery `message_outbox` pipeline.
- Continue using virtual host `/dongqh`, the existing `JacksonJsonMessageConverter`, and `luckyhub.messaging.enabled`.
- Use PowerShell 7 commands and UTF-8 for all repository work.

---

### Task 1: Add the fulfillment Outbox schema and event contract

**Files:**
- Create: `marketing-service/src/main/resources/db/migration/V28__add_fulfillment_outbox.sql`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/event/FulfillmentTaskReadyEvent.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/outbox/FulfillmentOutboxStatus.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/outbox/FulfillmentOutbox.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/outbox/FulfillmentOutboxMapper.java`
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentOutboxSchemaTests.java`

**Interfaces:**
- Produces: `FulfillmentTaskReadyEvent(String eventId, String fulfillmentNo, LocalDateTime occurredAt)`.
- Produces: `FulfillmentOutbox.pending(String eventId, String fulfillmentNo, String eventType, String payload)` with status `PENDING` and retry count `0`.
- Produces: `FulfillmentOutboxMapper.selectRelayCandidates(now, limit)`, `claimForRelay(id, token, now, leaseUntil)`, `markSent(id, token, sentAt)`, and `markFailed(id, token, error, nextRetryAt)`.

- [ ] **Step 1: Write the failing schema and event tests**

Add a Spring integration test that queries `information_schema.columns`, `information_schema.statistics`, and the event constructor:

```java
@SpringBootTest
class FulfillmentOutboxSchemaTests {
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsLeaseBasedFulfillmentOutbox() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'fulfillment_outbox'
                """, Integer.class)).isEqualTo(13);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'fulfillment_outbox'
                  AND index_name = 'uk_fulfillment_outbox_event_id'
                """, Integer.class)).isOne();
    }

    @Test
    void taskReadyEventRejectsMissingIdentity() {
        assertThatThrownBy(() -> new FulfillmentTaskReadyEvent(
                " ", "TASK-1", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
mvn -pl marketing-service -Dtest=FulfillmentOutboxSchemaTests test
```

Expected: test compilation fails because `FulfillmentTaskReadyEvent` does not exist, or Flyway/schema assertion fails because `fulfillment_outbox` does not exist.

- [ ] **Step 3: Add the migration and domain types**

Create `V28__add_fulfillment_outbox.sql` with the exact columns from the approved design: `id`, `event_id`, `fulfillment_no`, `event_type`, `payload`, `status`, `retry_count`, `last_error`, `next_retry_at`, `claim_token`, `claim_until`, `created_at`, `sent_at`; add unique `event_id`, an index on `(status, next_retry_at, claim_until, id)`, and a status check for `PENDING`, `PROCESSING`, `SENT`, `FAILED`.

Implement the event validation:

```java
public record FulfillmentTaskReadyEvent(
        String eventId,
        String fulfillmentNo,
        LocalDateTime occurredAt) {
    public FulfillmentTaskReadyEvent {
        eventId = required(eventId, "eventId");
        fulfillmentNo = required(fulfillmentNo, "fulfillmentNo");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
```

Implement the entity and mapper. Candidate SQL must select `PENDING`, due `FAILED`, and expired `PROCESSING`; claim/update SQL must repeat those conditions and require the matching claim token when marking sent or failed.

- [ ] **Step 4: Re-run and verify GREEN**

Run the focused test again. Expected: `FulfillmentOutboxSchemaTests` passes with no Flyway validation error.

- [ ] **Step 5: Commit the isolated schema/contract change**

```powershell
git add -- marketing-service/src/main/resources/db/migration/V28__add_fulfillment_outbox.sql marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentOutboxSchemaTests.java
git commit -m "feat: add fulfillment outbox schema"
```

---

### Task 2: Write Outbox records in task transactions

**Files:**
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/outbox/FulfillmentOutboxService.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/outbox/FulfillmentOutboxServiceImpl.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/service/impl/FulfillmentTaskServiceImpl.java`
- Modify: `marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentTaskServiceTests.java`
- Modify: cleanup in existing fulfillment integration tests that delete `fulfillment_task` rows.

**Interfaces:**
- Produces: `void FulfillmentOutboxService.appendReady(String fulfillmentNo)` with `Propagation.MANDATORY`.
- Consumes: `FulfillmentOutboxMapper.insert(FulfillmentOutbox)`.
- `FulfillmentTaskService.create` and `retryQuarantined` append only when they perform a new state transition.

- [ ] **Step 1: Add failing transaction and idempotency assertions**

Extend `FulfillmentTaskServiceTests`:

```java
@Autowired TransactionTemplate transactions;

@Test
void createsTaskAndOutboxAtomicallyWithoutDuplicatingIdempotentReplay() {
    CreateFulfillmentTaskCommand command = command(
            "OUTBOX-1", FulfillmentType.POINTS,
            new PointsFulfillmentPayload(100, "奖励"));
    service.create(command);
    service.create(command);

    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM fulfillment_outbox WHERE fulfillment_no='OUTBOX-1'",
            Integer.class)).isOne();
}

@Test
void outerRollbackRemovesBothTaskAndOutbox() {
    assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
        service.create(command("ROLLBACK-1", FulfillmentType.POINTS,
                new PointsFulfillmentPayload(100, "奖励")));
        throw new IllegalStateException("force rollback");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM fulfillment_task WHERE fulfillment_no='ROLLBACK-1'",
            Integer.class)).isZero();
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM fulfillment_outbox WHERE fulfillment_no='ROLLBACK-1'",
            Integer.class)).isZero();
}
```

Add assertions to the manual retry/terminate test: retry adds one new Outbox row; terminate adds none.

- [ ] **Step 2: Run and verify RED**

```powershell
mvn -pl marketing-service -Dtest=FulfillmentTaskServiceTests,FulfillmentSafetyTests test
```

Expected: Outbox count assertions fail with zero rows.

- [ ] **Step 3: Implement transaction-mandatory append**

`appendReady` must build one UUID event, serialize it, and insert `PENDING`:

```java
@Transactional(propagation = Propagation.MANDATORY)
public void appendReady(String fulfillmentNo) {
    FulfillmentTaskReadyEvent event = new FulfillmentTaskReadyEvent(
            UUID.randomUUID().toString(), fulfillmentNo, LocalDateTime.now());
    FulfillmentOutbox row = FulfillmentOutbox.pending(
            event.eventId(), fulfillmentNo, "FULFILLMENT_TASK_READY",
            objectMapper.writeValueAsString(event));
    mapper.insert(row);
}
```

Mark `create` transactional. Call `appendReady` only after a successful new task insert; keep the existing fast return for identical tasks and duplicate-key race losers. In `retryQuarantined`, append after the task and quarantine updates. Do not append in `terminate`.

- [ ] **Step 4: Make integration cleanup safe and verify GREEN**

Before deleting `fulfillment_task`, delete `fulfillment_outbox` in every touched fulfillment integration test. Re-run the focused tests; expected: all pass, including rollback and concurrent duplicate creation with exactly one Outbox.

- [ ] **Step 5: Commit**

```powershell
git add -- marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/outbox marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/service/impl/FulfillmentTaskServiceImpl.java marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment
git commit -m "feat: append fulfillment triggers transactionally"
```

---

### Task 3: Add the reliable Rabbit publisher, topology, and Relay

**Files:**
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/rabbit/FulfillmentRabbitTopology.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/rabbit/FulfillmentEventPublisher.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/rabbit/RabbitFulfillmentEventPublisher.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/config/FulfillmentMessagingConfiguration.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/outbox/FulfillmentOutboxRelay.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/scheduler/FulfillmentOutboxRelayScheduler.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/config/FulfillmentProperties.java`
- Modify: `marketing-service/src/main/resources/application.yaml`
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment/messaging/FulfillmentRabbitTopologyTests.java`
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment/messaging/RabbitFulfillmentEventPublisherTests.java`
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment/messaging/FulfillmentOutboxRelayTests.java`

**Interfaces:**
- `FulfillmentEventPublisher.publish(FulfillmentTaskReadyEvent)` returns only after a routable Broker ACK.
- `FulfillmentOutboxRelay.relayBatch()` returns the count marked `SENT`.
- `FulfillmentProperties` adds positive `outboxInterval`, non-negative `outboxInitialDelay`, and `outboxBatchSize` from 1 to 1000.

- [ ] **Step 1: Write failing topology, publisher, and Relay tests**

Topology test must assert two `DirectExchange` values, two durable queues, `x-dead-letter-exchange=fulfillment.dlx`, and bindings:

```java
assertThat(bindings).containsExactlyInAnyOrder(
        "fulfillment.direct:fulfillment.task.ready",
        "fulfillment.dlx:#");
```

Publisher tests must complete `CorrelationData.getFuture()` with ACK/NACK, attach a `ReturnedMessage` for the unroutable case, and leave the future incomplete for timeout. Relay tests must mock candidate selection and claim, then assert `markSent` happens only after publisher success; malformed JSON and publisher failure call `markFailed` and never `markSent`.

- [ ] **Step 2: Run and verify RED**

```powershell
mvn -pl marketing-service -Dtest=FulfillmentRabbitTopologyTests,RabbitFulfillmentEventPublisherTests,FulfillmentOutboxRelayTests test
```

Expected: compilation fails because the new messaging classes do not exist.

- [ ] **Step 3: Implement the exact topology and correlated-confirm publisher**

Define constants:

```java
public static final String EXCHANGE = "fulfillment.direct";
public static final String READY_KEY = "fulfillment.task.ready";
public static final String EXECUTE_QUEUE = "marketing.fulfillment.execute.queue";
public static final String DEAD_LETTER_EXCHANGE = "fulfillment.dlx";
public static final String DEAD_LETTER_QUEUE = "marketing.fulfillment.execute.dlq";
```

Use `RabbitTemplate.convertAndSend(EXCHANGE, READY_KEY, event, correlation)`, wait for `MessagingProperties.publisherConfirmTimeout()`, reject Return/NACK/timeout/interruption, and preserve interrupt status.

- [ ] **Step 4: Implement lease Relay and schedule**

Mirror the proven lottery Relay algorithm but use `claim_until` separately from `next_retry_at`. Failure delay is `min(300 seconds, 2^min(retry, 8))`, and `last_error` is capped at 500 characters. Schedule with:

```java
@Scheduled(
    fixedDelayString = "${luckyhub.fulfillment.outbox-interval:5s}",
    initialDelayString = "${luckyhub.fulfillment.outbox-initial-delay:60s}")
```

Add YAML values `outbox-interval: 5s`, `outbox-initial-delay: 60s`, and `outbox-batch-size: 100`. Guard publisher, Relay, listener-era components, and relay scheduler with `luckyhub.messaging.enabled=true`.

- [ ] **Step 5: Re-run and verify GREEN**

Run the three focused test classes. Expected: ACK path marks `SENT`; NACK, Return, timeout, malformed payload, and publisher exception are recorded as failed; topology assertions pass.

- [ ] **Step 6: Commit**

```powershell
git add -- marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/config marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/scheduler/FulfillmentOutboxRelayScheduler.java marketing-service/src/main/resources/application.yaml marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment/messaging
git commit -m "feat: relay fulfillment triggers to rabbitmq"
```

---

### Task 4: Consume a message by atomically claiming one task

**Files:**
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/service/FulfillmentStateService.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/service/impl/FulfillmentStateServiceImpl.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/worker/FulfillmentWorker.java`
- Create: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/rabbit/FulfillmentTaskReadyListener.java`
- Modify: `marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentWorkerTests.java`
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment/messaging/FulfillmentTaskReadyListenerTests.java`

**Interfaces:**
- Produces: `Optional<FulfillmentClaim> claimReady(String fulfillmentNo, Duration leaseDuration)`.
- Produces: `boolean FulfillmentWorker.runOne(String fulfillmentNo)`.
- Listener consumes `FulfillmentTaskReadyEvent` from `marketing.fulfillment.execute.queue`.

- [ ] **Step 1: Write failing specified-task and duplicate-delivery tests**

Create two pending tasks, call `runOne("TARGET-1")`, and assert only that task receives an attempt. Call `runOne` again and assert it returns false with no second gateway call. Add terminal and `PROCESSING` cases returning false.

Listener unit test:

```java
@Test
void delegatesOnlyTheEventTaskAndTreatsNoopAsSuccess() {
    FulfillmentTaskReadyEvent event = new FulfillmentTaskReadyEvent(
            "event-1", "TARGET-1", LocalDateTime.now());
    listener.onReady(event);
    verify(worker).runOne("TARGET-1");
}
```

Add a test where `worker.runOne` throws `DataAccessResourceFailureException`; the listener must propagate it so the container retry/DLQ policy can act.

- [ ] **Step 2: Run and verify RED**

```powershell
mvn -pl marketing-service -Dtest=FulfillmentWorkerTests,FulfillmentTaskReadyListenerTests test
```

Expected: compilation fails because `runOne`, `claimReady`, and the listener do not exist.

- [ ] **Step 3: Implement single-task claim**

Inside a transaction, select by `fulfillment_no` with `FOR UPDATE`. Claim only when status is `PENDING` and `next_attempt_at` is null or due; set `PROCESSING`, a UUID lease token, and `lease_until`. Return `Optional.empty()` for missing, terminal, busy, retry-waiting, reconciliation, or not-yet-due tasks.

- [ ] **Step 4: Refactor Worker around one shared execution method**

Implement:

```java
public boolean runOne(String fulfillmentNo) {
    return state.claimReady(fulfillmentNo, properties.leaseDuration())
            .map(claim -> {
                executeClaim(claim);
                return true;
            })
            .orElse(false);
}
```

Extract existing route/timing/`recordResult`/logistics projection work to `executeClaim`. Continue converting gateway runtime exceptions to `GatewayResult.retryable("GATEWAY_EXCEPTION", "Gateway调用异常")`; do not swallow database exceptions from claiming or result persistence.

- [ ] **Step 5: Add the listener and verify GREEN**

The listener method only validates the non-null event and calls `worker.runOne(event.fulfillmentNo())`. Do not add a consumer idempotency table: task status and lease acquisition are the idempotency boundary. Re-run focused tests; expected: exactly one task and one attempt execute, duplicate delivery is a successful no-op, and infrastructure failures propagate.

- [ ] **Step 6: Commit**

```powershell
git add -- marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/service marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/worker/FulfillmentWorker.java marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/messaging/rabbit/FulfillmentTaskReadyListener.java marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment
git commit -m "feat: execute fulfillment task from ready event"
```

---

### Task 5: Restrict the scheduler to compensation work

**Files:**
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/config/FulfillmentProperties.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/service/FulfillmentStateService.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/service/impl/FulfillmentStateServiceImpl.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/worker/FulfillmentWorker.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/scheduler/FulfillmentScheduler.java`
- Modify: `marketing-service/src/main/resources/application.yaml`
- Modify: `marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentRecoveryTests.java`
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment/FulfillmentCompensationSchedulerTests.java`

**Interfaces:**
- Produces: `List<FulfillmentClaim> claimCompensationDue(int limit, Duration leaseDuration, Duration pendingGrace)`.
- Produces: `int FulfillmentWorker.runCompensationBatch()`.
- `FulfillmentScheduler.poll()` first recovers expired leases, then runs one compensation batch.

- [ ] **Step 1: Write failing grace and recovery tests**

Create a fresh `PENDING` task and assert `runCompensationBatch()` returns zero. Age its `created_at` beyond 30 seconds and assert it is processed. Verify an immediately due `RETRY_WAITING` task and a recovered `RECONCILING` task do not wait for the pending grace.

Scheduler unit test must verify order:

```java
InOrder order = inOrder(state, worker);
scheduler.poll();
order.verify(state).recoverExpiredLeases(properties.recoveryBatchSize());
order.verify(worker).runCompensationBatch();
```

- [ ] **Step 2: Run and verify RED**

```powershell
mvn -pl marketing-service -Dtest=FulfillmentRecoveryTests,FulfillmentCompensationSchedulerTests test
```

Expected: new methods are missing or fresh pending tasks are still picked up immediately.

- [ ] **Step 3: Implement the compensation query**

The locked candidate query must be equivalent to:

```sql
WHERE (
    status IN ('RETRY_WAITING', 'RECONCILING')
    AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
) OR (
    status = 'PENDING'
    AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
    AND created_at <= :pendingBefore
)
ORDER BY id
LIMIT :limit
FOR UPDATE SKIP LOCKED
```

Add positive `compensationGrace` to `FulfillmentProperties` and `compensation-grace: 30s` to YAML. Preserve the existing operation rule: `RECONCILING` creates `QUERY`; other claimed states create `EXECUTE`.

- [ ] **Step 4: Change scheduler responsibility and verify GREEN**

Replace `worker.runBatch()` with `worker.runCompensationBatch()`. Update old worker/recovery tests to use `runOne` when testing normal immediate execution and `runCompensationBatch` only when testing due retry/reconciliation. Run both focused test classes and the full `com.dongqh.luckyhub.fulfillment` package; expected: fresh pending is not scanned, but aged pending, due retry, reconciliation, and expired lease paths pass.

- [ ] **Step 5: Commit**

```powershell
git add -- marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/config/FulfillmentProperties.java marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/service marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/worker/FulfillmentWorker.java marketing-service/src/main/java/com/dongqh/luckyhub/fulfillment/scheduler/FulfillmentScheduler.java marketing-service/src/main/resources/application.yaml marketing-service/src/test/java/com/dongqh/luckyhub/fulfillment
git commit -m "refactor: make fulfillment polling compensatory"
```

---

### Task 6: Verify contracts, regression safety, and local routing

**Files:**
- Modify only when verification exposes a scoped defect: files introduced or changed in Tasks 1-5.

**Interfaces:**
- No new interfaces. This task proves the approved design and existing API compatibility.

- [ ] **Step 1: Run all fulfillment and messaging tests**

```powershell
mvn -pl marketing-service -Dtest='com.dongqh.luckyhub.fulfillment.**,com.dongqh.luckyhub.lottery.messaging.**' test
```

Expected: zero failures and zero errors.

- [ ] **Step 2: Run the complete marketing-service suite**

```powershell
mvn -pl marketing-service test
```

Expected: Maven `BUILD SUCCESS`; no Flyway checksum or context startup failures.

- [ ] **Step 3: Compile the affected reactor**

```powershell
mvn -pl marketing-service -am -DskipTests package
```

Expected: Maven `BUILD SUCCESS`.

- [ ] **Step 4: Inspect the local RabbitMQ topology when the stack is available**

Use the existing Compose/RabbitMQ management tooling to verify `fulfillment.direct`, `fulfillment.task.ready`, `marketing.fulfillment.execute.queue`, `fulfillment.dlx`, and `marketing.fulfillment.execute.dlq`. Publish one uniquely named task-ready probe only if a matching disposable database task has been created; otherwise restrict the check to topology and binding inspection so no production-like fulfillment is triggered.

- [ ] **Step 5: Review scope and evidence**

```powershell
git status --short
git diff --stat HEAD~5..HEAD
git log -6 --oneline
```

Confirm no benefit projection code changed, admin controller contracts are unchanged, lottery `message_outbox` code is unchanged, and all gateway calls still occur outside database transactions. Report the exact test counts, build results, migration, topology, Outbox retry behavior, consumer idempotency, and compensation behavior; then stop before any next migration.
