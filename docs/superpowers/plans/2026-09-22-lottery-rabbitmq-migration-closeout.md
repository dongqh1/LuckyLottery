# Lottery RabbitMQ Migration Closeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the lottery RabbitMQ feature switch and prove the Outbox-to-RabbitMQ-to-idempotent-consumer path against real MySQL, Redis, and RabbitMQ services.

**Architecture:** Keep `MessagingProperties` registered for database and fulfillment components, but apply the existing `luckyhub.messaging.enabled` condition to the Rabbit converter and topology beans so disabled messaging creates no Rabbit infrastructure. Retain the transactional Outbox and database/Redis state machines, then verify transport and idempotency separately and together using the repository's existing Docker Compose services.

**Tech Stack:** Java 17, Spring Boot 4, Spring AMQP, MyBatis-Plus, MySQL 8, Redis 7, RabbitMQ 4, JUnit 5, AssertJ, Mockito, Maven, Docker Compose.

## Global Constraints

- Do not remove the database Outbox, lottery database state, Redis quota/Lua logic, distributed locks, or compensation schedulers.
- Do not introduce Testcontainers or a new infrastructure dependency.
- Do not change synchronous lottery or admin APIs.
- Read credentials from `.env` or environment variables; never print or copy secret values into source files or logs.
- Do not run `git commit`; leave all changes local for the user's final combined commit.
- Use PowerShell 7 commands on Windows and preserve UTF-8 text.

---

### Task 1: Make the lottery RabbitMQ configuration obey the global message switch

**Files:**
- Modify: `marketing-service/src/test/java/com/dongqh/luckyhub/lottery/messaging/RabbitTopologyConfigurationTests.java`
- Modify: `marketing-service/src/main/java/com/dongqh/luckyhub/lottery/config/LotteryMessagingConfiguration.java`

**Interfaces:**
- Consumes: property `luckyhub.messaging.enabled` with `true` as the missing-property default.
- Produces: conditional beans named `rabbitMessageConverter` and `lotteryRabbitDeclarables`.

- [ ] **Step 1: Add a failing configuration-switch test**

Add an `ApplicationContextRunner` configured with `LotteryMessagingConfiguration.class`. Add two tests: one with `luckyhub.messaging.enabled=false` asserting `MessagingProperties` remains available while `MessageConverter` and `Declarables` are absent; and one with the property omitted asserting both Rabbit beans are present.

```java
private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(LotteryMessagingConfiguration.class);

@Test
void doesNotCreateLotteryRabbitInfrastructureWhenMessagingIsDisabled() {
    contextRunner.withPropertyValues("luckyhub.messaging.enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(LotteryMessagingConfiguration.class);
                assertThat(context).hasSingleBean(MessagingProperties.class);
                assertThat(context).doesNotHaveBean(MessageConverter.class);
                assertThat(context).doesNotHaveBean(Declarables.class);
            });
}

@Test
void createsLotteryRabbitInfrastructureWhenMessagingSwitchIsMissing() {
    contextRunner.run(context -> {
        assertThat(context).hasSingleBean(LotteryMessagingConfiguration.class);
        assertThat(context).hasSingleBean(MessageConverter.class);
        assertThat(context).hasSingleBean(Declarables.class);
    });
}
```

- [ ] **Step 2: Run the focused test and verify the disabled case fails**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17.0.5'
mvn -o '-Dmaven.repo.local=C:\Users\41227\.m2\repository' -pl marketing-service -am '-Dtest=RabbitTopologyConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: the new disabled-switch test fails because the Rabbit infrastructure beans currently load unconditionally.

- [ ] **Step 3: Add the minimal configuration condition**

Add the conditional annotation to both Rabbit infrastructure bean methods while leaving `@EnableConfigurationProperties(MessagingProperties.class)` unconditional:

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@ConditionalOnProperty(
        prefix = "luckyhub.messaging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public MessageConverter rabbitMessageConverter() {
```

- [ ] **Step 4: Re-run the focused test and verify all topology and switch tests pass**

Run the command from Step 2. Expected: all tests in `RabbitTopologyConfigurationTests` pass.

---

### Task 2: Add a real RabbitMQ transport and idempotent-consumer integration test

**Files:**
- Create: `marketing-service/src/test/java/com/dongqh/luckyhub/lottery/messaging/RabbitLotteryMessagingIntegrationTests.java`

**Interfaces:**
- Consumes: `DrawEventPublisher.publish(DrawEventEnvelope)`, production exchange/queue bindings, `MessageConsumeService`, MySQL tables `lottery_draw_order`, `lottery_draw_record`, `user_benefit`, `fulfillment_task`, `message_consume_record`, and the configured RabbitMQ connection.
- Produces: a repeatable integration test proving two deliveries of the same event create one consume record and one fulfillment task.

- [ ] **Step 1: Create the integration test fixture**

Use `@SpringBootTest` with messaging enabled and scheduling disabled. Inject `DrawEventPublisher`, `ObjectMapper`, `JdbcTemplate`, and `RabbitAdmin`. Build unique positive IDs and a unique request/event ID. Insert the same minimal coupon reward fixture used by `LotteryRewardConcurrencyTests`: draw order, draw record, and pending user benefit.

```java
@SpringBootTest(properties = {
        "seata.enabled=false",
        "spring.task.scheduling.enabled=false",
        "luckyhub.messaging.enabled=true",
        "luckyhub.lottery.outbox-initial-delay=24h",
        "luckyhub.fulfillment.initial-delay=1h"
})
class RabbitLotteryMessagingIntegrationTests {
    @Autowired DrawEventPublisher publisher;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitAdmin rabbitAdmin;
}
```

- [ ] **Step 2: Add bounded polling and exact cleanup**

Implement a local `awaitCount` helper that polls every 100 ms for at most 10 seconds and fails with the last observed count. In `@AfterEach`, delete only rows connected to the generated event ID and request ID, in foreign-key order. Purge `LotteryRabbitTopology.EVENT_QUEUE` before each test so stale local messages cannot satisfy the assertion.

```java
private void awaitCount(String sql, long expected, Object... args) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    long actual;
    do {
        actual = jdbc.queryForObject(sql, Long.class, args);
        if (actual == expected) return;
        Thread.sleep(100);
    } while (System.nanoTime() < deadline);
    assertThat(actual).isEqualTo(expected);
}
```

- [ ] **Step 3: Add the real-broker duplicate-delivery test**

Create one `PRIZE_FULFILLMENT_REQUESTED` envelope and publish the identical envelope twice. Wait until both idempotent database counts equal one.

```java
publisher.publish(envelope);
publisher.publish(envelope);

awaitCount("SELECT COUNT(*) FROM message_consume_record WHERE event_id=?", 1L,
        envelope.eventId().toString());
awaitCount("SELECT COUNT(*) FROM fulfillment_task WHERE source_id=?", 1L,
        Long.toString(benefitId));
```

Also assert the production exchange, event queue, dead-letter exchange, and dead-letter queue are declared through `RabbitAdmin.getQueueInfo(...)` and exchange declaration calls.

- [ ] **Step 4: Run the new test before starting infrastructure and record the expected connection failure**

Run the focused Maven test with `luckyhub.messaging.enabled=true`. Expected before Docker startup: fail with a RabbitMQ or MySQL connection error, demonstrating that this is an actual infrastructure test rather than a mock transport test.

---

### Task 3: Start real infrastructure and execute the integration suite

**Files:**
- Read: `.env`
- Read: `compose.yaml`
- Test: `marketing-service/src/test/java/com/dongqh/luckyhub/lottery/messaging/OutboxPersistenceIntegrationTests.java`
- Test: `marketing-service/src/test/java/com/dongqh/luckyhub/lottery/messaging/RabbitLotteryMessagingIntegrationTests.java`

**Interfaces:**
- Consumes: Docker Compose services `mysql`, `schema-init`, `redis`, and `rabbitmq`.
- Produces: healthy services plus passing real MySQL and RabbitMQ integration evidence.

- [ ] **Step 1: Validate Docker and required environment configuration without revealing secrets**

Run `docker version`, `docker compose config --services`, and a PowerShell check that reports only whether `.env` exists and whether required keys are non-empty. Do not output values.

- [ ] **Step 2: Start the minimum service set**

Run:

```powershell
docker compose up -d mysql redis rabbitmq schema-init
```

If image download is blocked by sandbox networking, request the necessary command escalation and rerun the same command.

- [ ] **Step 3: Wait on Compose health checks**

Poll `docker compose ps --format json` for at most 120 seconds. Continue only after MySQL, Redis, and RabbitMQ report healthy and `schema-init` exits successfully. On failure, capture `docker compose logs --tail 200 <service>` for only the failed service.

- [ ] **Step 4: Run the real MySQL Outbox test**

Load the non-secret connection settings from `.env` into the Maven process and account for the host MySQL port from Compose. Run:

```powershell
mvn -o '-Dmaven.repo.local=C:\Users\41227\.m2\repository' -pl marketing-service -am '-Dtest=OutboxPersistenceIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: transaction commit/rollback, JSON round-trip, and duplicate event ID tests all pass.

- [ ] **Step 5: Run the real RabbitMQ end-to-end test**

Run the same Maven shape for `RabbitLotteryMessagingIntegrationTests`, with host RabbitMQ and Redis ports plus credentials available to Spring. Expected: the broker accepts confirmed publications, the production queue delivers both copies, and MySQL contains exactly one consume record and one fulfillment task.

---

### Task 4: Run regression verification and prepare the uncommitted handoff

**Files:**
- Verify: `marketing-service/src/main/java/com/dongqh/luckyhub/lottery/**`
- Verify: `marketing-service/src/test/java/com/dongqh/luckyhub/lottery/**`
- Verify: `marketing-service/src/main/resources/application.yaml`
- Verify: `docs/superpowers/specs/2026-09-22-lottery-rabbitmq-migration-closeout-design.md`
- Verify: `docs/superpowers/plans/2026-09-22-lottery-rabbitmq-migration-closeout.md`

**Interfaces:**
- Consumes: all changes and test evidence from Tasks 1-3.
- Produces: a precise completion report and local change list without a commit.

- [ ] **Step 1: Run the focused regression suite**

Run the Rabbit topology, publisher, listener, Outbox Relay, Outbox persistence, real Rabbit integration, message consumer, and reward concurrency tests in one Maven invocation. Expected: zero failures and zero errors while infrastructure is healthy.

- [ ] **Step 2: Compile/package the affected reactor without rerunning tests**

```powershell
mvn -o '-Dmaven.repo.local=C:\Users\41227\.m2\repository' -pl marketing-service -am -DskipTests package
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Verify legacy Redis Stream transport is absent**

Run `rg` over `marketing-service/src/main` for `StreamMessageListener`, `opsForStream`, `XADD`, `XREADGROUP`, `XACK`, `lottery-stream`, and `lottery-group`. Expected: no matches.

- [ ] **Step 4: Review the exact local diff**

Run `git diff --check`, `git status --short`, and targeted diffs for the configuration and new tests. Confirm no secrets, generated files, or unrelated reversions were introduced.

- [ ] **Step 5: Report results without committing**

Report the switch behavior, real-service health, test counts, package result, any environmental limitations, and the exact files created or modified. Explicitly state that no Git commit was created.
