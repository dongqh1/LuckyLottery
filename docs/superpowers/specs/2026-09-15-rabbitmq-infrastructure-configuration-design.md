# RabbitMQ Infrastructure Configuration Design

## Goal

Add a reproducible RabbitMQ runtime and connection configuration to the project without switching the existing Marketing Redis Stream producer or consumer implementation.

## Scope

- Run RabbitMQ 4.3.5 through the existing Docker Compose project.
- Persist broker data in a named Docker volume.
- Store local development credentials in the uncommitted `.env` file.
- Document safe placeholders in `.env.example`.
- Preconfigure Marketing's `spring.rabbitmq` connection properties through environment variables.
- Keep `luckyhub.messaging.provider: redis-stream` unchanged.
- Do not add RabbitMQ publishers, listeners, exchanges, queues, bindings, or business-service dependencies in this phase.

## Container Design

Add a `rabbitmq` service to `compose.yaml` using the pinned image `rabbitmq:4.3.5-management-alpine`.

The service will:

- use hostname `rabbitmq` on the existing `hmall-lottery_default` network;
- expose AMQP port `5672` and management port `15672` through configurable host ports;
- initialize the default user, password, and virtual host from environment variables;
- persist `/var/lib/rabbitmq` in a `rabbitmq-data` named volume;
- use `rabbitmq-diagnostics -q ping` for its health check;
- use `restart: unless-stopped` consistently with other infrastructure services.

The service will not be added as a required dependency of Marketing yet because Redis Stream remains the active provider.

## Configuration Design

The local `.env` file will contain the actual development values:

- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `RABBITMQ_VHOST`
- `RABBITMQ_PORT`
- `RABBITMQ_MANAGEMENT_PORT`

`.env.example` will contain non-secret replacement values and the same variable names.

Marketing's `application.yaml` will define `spring.rabbitmq` host, port, username, password, and virtual host using environment variables. Docker Compose will supply `rabbitmq` as the container-network host and port `5672`. Local JVM runs will default to `localhost` and the configured host port.

No AMQP runtime dependency is added to Marketing in this phase, avoiding an unintended startup or health dependency before the actual provider migration.

## Security

- The requested local username and password will differ from the tutorial values.
- Actual credentials stay in `.env`; they are not copied to `.env.example`, source code, or documentation.
- The default `guest/guest` account is not used.
- The management UI is intended for local development only.

## Verification

Automated infrastructure contracts will verify:

- the exact pinned RabbitMQ image;
- ports, named volume, health check, network, and environment mappings;
- presence of the expected `.env.example` variables;
- Marketing connection placeholders;
- Redis Stream remains the selected provider;
- Marketing does not depend on RabbitMQ startup in this phase.

Runtime verification will:

- render the Compose configuration;
- pull/start the RabbitMQ service, with progress updates while the image is downloading;
- wait for the container to become healthy;
- verify broker identity and enabled management plugin;
- authenticate against the management API using the credentials stored in `.env` without printing the password.

