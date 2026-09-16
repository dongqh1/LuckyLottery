# Pay Success RabbitMQ Message Design

## Goal

Replace the invalid multi-argument RabbitMQ send and listener signatures with one shared JSON message contract for payment-success events.

## Design

- Add `PaySuccessMessage` to `hm-api` so both Pay and Trade use the same contract.
- The message contains `Long orderId`, `Long userId`, and `Integer amount`.
- Pay publishes one `PaySuccessMessage` to exchange `pay.direct` with routing key `pay.success`.
- Trade consumes one `PaySuccessMessage` and passes its fields to the existing order service method.
- Remove the synchronous `tradeClient.markOrderPaySuccess(...)` call after publishing so the same state transition is not executed twice.
- Configure Spring AMQP with Boot 4's `JacksonJsonMessageConverter` and the AMQP `MessageConverter` interface.

## Error Handling and Compatibility

- Keep the durable queue, exchange, and routing key names unchanged.
- Keep the existing order-service validation and idempotent state transition unchanged.
- Do not add message headers for business payload fields.
- Do not use the deprecated Jackson 2 AMQP converter.

## Verification

- A contract test verifies the shared message type and sender/listener signatures.
- Compilation verifies that the converter uses the Spring AMQP interface and Boot 4 converter.
- Existing Pay and Trade tests remain green.
