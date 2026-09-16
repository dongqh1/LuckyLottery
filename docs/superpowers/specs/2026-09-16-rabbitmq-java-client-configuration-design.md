# RabbitMQ Java Client Configuration Design

## Goal

Complete the existing RabbitMQ infrastructure setup by configuring the Java services that already use or declare AMQP support. Use the virtual host `/dongqh`, keep credentials in the root `.env`, and do not publish RabbitMQ settings to Nacos.

## Scope

- Configure `marketing-service`, `trade-service`, and `pay-service` through each service's `application.yaml`.
- Keep the RabbitMQ username, password, virtual host, and exposed ports in the root `.env`.
- Keep only non-secret placeholders in `.env.example`.
- Pass the same RabbitMQ environment variables to all three services from `compose.yaml`.
- Create `/dongqh` in the running RabbitMQ instance and grant the configured user full permissions on that virtual host.
- Preserve the current messaging-provider behavior; this change does not migrate Marketing from Redis Stream to RabbitMQ.

## Configuration Design

Each Java service will use Spring Boot's standard `spring.rabbitmq` properties:

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:}
    password: ${RABBITMQ_PASSWORD:}
    virtual-host: ${RABBITMQ_VHOST:/dongqh}
```

Local JVM runs resolve the project-root `.env` through the existing Spring config-import pattern and connect to `localhost`. Docker Compose supplies `rabbitmq` as `RABBITMQ_HOST` and uses container port `5672`.

The root Compose service will use `/dongqh` as its default virtual host for fresh RabbitMQ data volumes. Existing persistent volumes require an explicit `rabbitmqctl add_vhost` and `set_permissions` operation because changing `RABBITMQ_DEFAULT_VHOST` does not reinitialize an existing broker database.

## Credentials and Security

- The real RabbitMQ password remains only in the ignored root `.env`.
- `.env.example` contains replaceable sample values.
- No password is hard-coded in Java YAML, Maven files, or committed Compose configuration.
- No RabbitMQ credentials are added to Nacos.

## Verification

- Validate the resolved Compose configuration.
- Run the RabbitMQ infrastructure contract tests, updated for `/dongqh` and all three Java services.
- Confirm the broker responds to diagnostics.
- Confirm `/dongqh` exists and the configured user has permissions for `configure`, `write`, and `read`.
- Check the final diff for accidental secrets and whitespace errors.

## Non-Goals

- No exchanges, queues, bindings, publishers, or consumers are added.
- No messaging-provider migration is performed.
- No Nacos shared configuration is created.
