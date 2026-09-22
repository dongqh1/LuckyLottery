# Item Database Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provision an isolated `hm_item` database and least-privilege runtime account, then point `item-service` at it without removing the legacy `hmall.item` table.

**Architecture:** The existing `schema-init` container remains the privileged migration boundary. A new idempotent V004 migration creates the database/account, clones the table, and copies missing rows; the runtime service receives only its dedicated credentials.

**Tech Stack:** MySQL 8.4, Docker Compose, Spring Boot 2.7 YAML configuration, Python infrastructure contract tests.

## Global Constraints

- Preserve `hmall.item` during this phase.
- Do not modify order/cart business code.
- Do not grant `hm_item_app` access to `hmall` or `luckyhub`.
- Do not store the real item database password in tracked files.

---

### Task 1: Define the infrastructure contract

**Files:**
- Modify: `tests/test_infrastructure_contract.py`

**Interfaces:**
- Consumes: repository SQL, Compose, environment template, and item service YAML files.
- Produces: executable assertions for the database, account grant, seed copy, and service connection.

- [x] Add assertions requiring `mysql/migrations/V004__create_item_service_database.sql`.
- [x] Assert Compose mounts and runs V004 with `HM_ITEM_DB_PASSWORD`.
- [x] Assert V004 creates `hm_item`, `hm_item_app`, DML-only grants, and an idempotent copy from `hmall.item`.
- [x] Assert item-service selects `hm_item` and `hm_item_app` without `root`.
- [x] Run `python -m unittest tests.test_infrastructure_contract` and confirm the new assertions fail because V004 and its wiring do not exist.

### Task 2: Implement database provisioning and service configuration

**Files:**
- Create: `mysql/migrations/V004__create_item_service_database.sql`
- Modify: `compose.yaml`
- Modify: `.env.example`
- Modify: `item-service/src/main/resources/application.yaml`
- Modify: `item-service/src/main/resources/application-dev.yaml`
- Modify: `item-service/src/main/resources/application-local.yaml`

**Interfaces:**
- Consumes: `hmall.item` and `HM_ITEM_DB_PASSWORD`.
- Produces: `hm_item.item`, MySQL user `hm_item_app`, and item-service datasource properties.

- [x] Add the idempotent SQL migration and DML-only grant.
- [x] Mount and execute V004 after the existing HMall migrations.
- [x] Add the example password variable and service datasource properties.
- [x] Run the infrastructure contract test and confirm it passes.
- [x] Run `mvn -pl :item-service -am clean package` with JDK 17 and confirm the module builds.

### Task 3: Apply and inspect the live database when available

**Files:**
- No repository files.

**Interfaces:**
- Consumes: the running Compose MySQL service and local `.env` credentials.
- Produces: an initialized `hm_item` schema and verified grants/data counts.

- [x] Check `docker compose ps` without starting or deleting services.
- [x] If MySQL is running, execute `docker compose run --rm schema-init`.
- [x] Verify `hm_item.item`, the runtime grant, and source/target row counts without displaying passwords.
