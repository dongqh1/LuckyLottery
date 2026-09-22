# Pay Success RabbitMQ Message Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish and consume payment-success data as one typed JSON RabbitMQ message.

**Architecture:** `hm-api` owns the shared `PaySuccessMessage` contract. Pay publishes that object once, Trade consumes it once, and Spring AMQP's Boot 4 Jackson 3 converter serializes and deserializes it.

**Tech Stack:** Java 17, Spring Boot 4.0.8, Spring AMQP 4.0.x, RabbitTemplate, JUnit 5, Mockito.

## Global Constraints

- Keep exchange `pay.direct`, routing key `pay.success`, and queue `trade.pay.success.queue` unchanged.
- Do not call the synchronous Trade payment-success endpoint after publishing the event.
- Use `JacksonJsonMessageConverter`, not deprecated `Jackson2JsonMessageConverter`.
- Use `org.springframework.amqp.support.converter.MessageConverter`.

---

### Task 1: Define and consume the shared event

**Files:**
- Create: `hm-api/src/main/java/com/dongqh/hmapi/dto/PaySuccessMessage.java`
- Create: `trade-service/src/test/java/com/dongqh/tradeservice/listener/PayStatusListenerTest.java`
- Modify: `trade-service/src/main/java/com/dongqh/tradeservice/listener/PayStatusListener.java`

**Interfaces:**
- Produces: `PaySuccessMessage(Long orderId, Long userId, Integer amount)`.
- Consumes: `IOrderService.markOrderPaySuccess(Long, Long, Integer)`.

- [ ] **Step 1: Add a listener test that invokes `listenPaySuccess(new PaySuccessMessage(101L, 7L, 8500))` and verifies `markOrderPaySuccess(101L, 7L, 8500)` once.**
- [ ] **Step 2: Run the focused Trade test and confirm it fails because the event type and one-argument listener do not exist.**
- [ ] **Step 3: Add the Java record and change the listener to accept one `PaySuccessMessage`.**
- [ ] **Step 4: Re-run the focused Trade test and confirm it passes.**

### Task 2: Publish one event and configure Boot 4 conversion

**Files:**
- Create: `tests/test_pay_success_rabbitmq_contract.py`
- Modify: `pay-service/src/main/java/com/dongqh/payservice/service/impl/PayOrderServiceImpl.java`
- Modify: `mall-backend/hm-common/src/main/java/com/hmall/common/config/MqConfig.java`
- Create: `mall-backend/hm-common/src/test/java/com/hmall/common/config/MqConfigTest.java`

**Interfaces:**
- Consumes: `PaySuccessMessage` from Task 1.
- Produces: one `RabbitTemplate.convertAndSend(String, String, Object)` call and an AMQP `MessageConverter` bean.

- [ ] **Step 1: Add a source contract asserting one `PaySuccessMessage` send, no `tradeClient.markOrderPaySuccess`, and Boot 4 converter imports.**
- [ ] **Step 2: Add `MqConfigTest` asserting `messageConverter()` is a `JacksonJsonMessageConverter`.**
- [ ] **Step 3: Run both focused tests and confirm they fail for the old sender and converter.**
- [ ] **Step 4: Publish `new PaySuccessMessage(order.getId(), order.getUserId(), order.getTotalFee())`, remove the duplicate Feign call, and replace the converter/imports.**
- [ ] **Step 5: Run focused tests, then compile `hm-common`, `hm-api`, `pay-service`, and `trade-service` with Maven running on JDK 17.**
- [ ] **Step 6: Run the existing Pay and Trade unit tests and inspect the final diff for unrelated changes.**
