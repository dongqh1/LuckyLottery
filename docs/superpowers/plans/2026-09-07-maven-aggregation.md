# Maven Aggregation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the repository a Maven reactor and make `item-service` build against the existing HMall Java 11 / Spring Boot 2.7 dependency baseline.

**Architecture:** Add an aggregator-only root POM that includes the existing backend, item, and marketing Maven projects. Keep `mall-backend` as the parent for legacy HMall modules and point `item-service` at that parent so `hm-common` is resolved from the same reactor.

**Tech Stack:** Maven 3.9.2, JDK 17 build runtime, Java 11 bytecode for HMall services, Spring Boot 2.7.12 for `item-service`.

## Global Constraints

- Do not migrate business source code in this change.
- Do not change `marketing-service` from Spring Boot 4.
- Preserve existing user changes outside Maven configuration.
- Verify from `E:\ANotes\software\hmall-lottery` with JDK 17.

---

### Task 1: Create the root reactor

**Files:**
- Create: `pom.xml`

**Interfaces:**
- Consumes: the existing `mall-backend/pom.xml`, `item-service/pom.xml`, and `marketing-service/pom.xml` Maven projects.
- Produces: a root Maven reactor in which `:item-service` can be selected.

- [x] Add an aggregator-only POM with `packaging=pom` and modules `mall-backend`, `item-service`, and `marketing-service`.
- [x] Run `mvn validate` with JDK 17 and confirm the reactor includes all nested modules.

### Task 2: Align item-service with HMall

**Files:**
- Modify: `item-service/pom.xml`

**Interfaces:**
- Consumes: parent `com.heima:hmall:1.0.0` from `mall-backend/pom.xml` and reactor module `com.heima:hm-common:1.0.0`.
- Produces: an `item-service` module compatible with HMall's Spring Boot 2.7.12 and Java 11 dependencies.

- [x] Replace the Spring Boot 4 parent with `com.heima:hmall:1.0.0` and relative path `../mall-backend/pom.xml`.
- [x] Replace Boot 4 MyBatis and MySQL coordinates with the versions managed by the HMall parent.
- [x] Add H2 in test scope so the generated context-load test does not require an external MySQL instance.
- [x] Run `mvn -pl :item-service -am clean package` with JDK 17 and confirm the selected reactor build succeeds.
