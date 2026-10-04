# Marketing Membership Service Compatibility-First Extraction Plan

> This is a gated implementation plan, not authorization to start the migration. Do not execute implementation tasks until every applicable gate below has evidence and the relevant contract decisions are approved.

**Goal:** Extract Marketing membership into the already-approved `membership-service` boundary while preserving current membership reads and benefit snapshots, separating verified paid purchases from reward grants, and ensuring one writer at every stage.

**Architecture:** `membership-service` becomes the sole owner of `membership_product`, `user_membership`, and `membership_grant_record` in `hm_membership`. Preserve the existing `/api/memberships/**` public contract during transition. Keep the old Marketing implementation as the sole writer until an explicit, validated cutover; never dual-write. Consumers use provider APIs/events rather than Membership mappers or cross-schema SQL. Trade/Pay own cash purchase and payment verification; Lottery/Fulfillment own reward eligibility and successful fulfillment; Membership applies an idempotent grant only after the corresponding authoritative event/command.

**Tech Stack:** Java 17, Spring Boot 4, MyBatis-Plus, Flyway, existing `hm-api` DTO/client conventions, RabbitMQ durable payment/fulfillment events, JUnit 5/Mockito, and isolated test databases. Production database access is not part of plan preparation.

## Global Constraints

- Preserve existing workspace changes and every untracked file; do not reset, clean, replace, or stage unrelated work.
- Keep the preflight status `未运行` unless an already-existing account is confirmed to have only `SELECT` privileges and access to all required schemas. Never create or modify DB accounts, use unknown application credentials, or infer a clean result from unavailable data.
- A database identity audit that reports any identity ambiguity, `hmall:{id}` projection anomaly, or RBAC orphan blocks user-keyed membership migration. Record only sanitized aggregates; do not place usernames, phone numbers, password hashes, credentials, or row-level account maps in Git/docs/handoff.
- Do not start Compose, schema-init, applications, or other stateful services as part of readiness work. Do not rewrite Marketing Flyway V1–V28; the new service begins with its own `hm_membership` migration history only after the data plan is approved.
- Do not switch Gateway/Nginx login or RBAC routes, modify account data, remove current Marketing membership/RBAC code, or begin `user-service` identity/RBAC migration in this plan-preparation task.
- Do not let the new service read/lock `sys_user`, copy RBAC tables, or claim a remote status check is equivalent to the existing same-transaction `FOR UPDATE` lock.
- Do not change Lottery Redis Lua, quota keys, Redisson locks, TTLs, or reconciliation behavior.
- Do not call the migration complete until module, data, API, authorization, consumer, rollback, and no-old-writer checks all pass. The target is one of the five planned Marketing replacements; this plan does not claim all ten business services exist.

## Hard Gates Before Implementation

1. **Identity and RBAC evidence:** Execute the existing identity/RBAC aggregate preflight only after the read-only credential and schema-access conditions are verified. Reconcile the approved stable user identifier and permission-enforcement path. Any ambiguity or orphan stops user-keyed membership migration; no automatic ID repair, account merge, or permission copy is allowed.
2. **Paid purchase authorization:** Define how Membership can establish that a purchase is paid from a durable, authoritative Pay event. Approve event identity, order domain/reference, user ID, product reference, replay behavior, and rejection behavior. The current public `POST /api/memberships/purchases` accepts a caller-provided `businessNo` and product ID and does not show Trade/Pay paid-state verification; it cannot be treated as a verified purchase command without a separately approved contract.
3. **Reward grant authorization:** Specify a grant path distinct from paid purchase. `fulfillment-service` may invoke it only after validating the reward task identity/snapshot and before reporting fulfillment success. It must carry an immutable product reference and use a tested idempotency key. Preserve `fulfillmentNo-M-index` only if the approved contract confirms stability, uniqueness, and length.
4. **Membership time semantics:** Decide whether `user_membership.starts_at` means first-ever activation or the start of the currently effective membership. Existing renewal and level-change code does not update this field for an existing row, and current tests do not settle that meaning. Preserve observed behavior until an explicit decision is approved.
5. **Product identity and snapshot contract:** Resolve whether Lottery’s numeric `rewardTargetId` remains a stable Membership product ID or is replaced with an approved immutable code/reference. Confirm that reward-definition validation, snapshot creation, fulfillment payload, grant request, and replay all refer to the same product even after data migration.
6. **User deactivation/concurrency semantics:** Approve what happens if a user is disabled while a payment or reward grant is in flight. The existing membership transaction locks `sys_user`; the new service cannot reproduce that cross-service lock. Define and test the replacement consistency rule before writes move.
7. **Authorization compatibility:** Define how existing `MEMBERSHIP_READ` and `MEMBERSHIP_MANAGE` behavior is enforced by the new provider using the approved shared security/user path. Do not create a second RBAC source or silently weaken endpoint permissions.

## File Map

- Create `membership-service/pom.xml`, `membership-service/src/main/java/com/dongqh/membershipservice/MembershipServiceApplication.java`, and service-local `config`, `controller`, `domain`, `mapper`, and `service` packages following the existing sibling-service structure.
- Create `membership-service/src/main/resources/application.yaml`, `application-local.yaml`, and `application-dev.yaml` using existing service configuration conventions; production credentials and grants are supplied through approved external configuration, never committed.
- Create `membership-service/src/main/resources/db/migration/V1__create_membership_tables.sql` for only the three Membership-owned tables and their validated indexes/constraints. Derive schema facts from old Marketing V12 without editing V1–V28.
- Create Membership provider tests under `membership-service/src/test/java/com/dongqh/membershipservice/` for controller/permission contracts, product reads, active-benefit semantics, paid grants, reward grants, idempotency conflicts, renewal/level-change time semantics, and concurrent grants.
- Existing database-audit preparation: `scripts/migration/marketing-identity-rbac-preflight.sql`, `tests/test_marketing_identity_rbac_preflight.py`, `docs/superpowers/audits/2026-09-24-marketing-identity-rbac-preflight-results.md`, `scripts/migration/marketing-membership-preflight.sql`, `tests/test_marketing_membership_preflight.py`, and `docs/superpowers/audits/2026-09-25-marketing-membership-preflight-results.md`. Static tests do not mean either SQL file has been run.
- Modify root `pom.xml` only to include `membership-service` after the module can build independently.
- Add `hm-api` membership DTOs/client only in the same implementation slice as a concrete provider and at least one real caller. The provider must not depend on its own client interface or expose persistence entities.
- Modify `trade-service` Membership benefit adapter/configuration and its tests only after the read contract is finalized. Modify `mall-backend/hm-service`’s existing Marketing benefit client only to keep its compatibility behavior and configuration explicit.
- Modify Lottery reward-definition/snapshot adapters to call the approved Membership product-read contract. Move reward-grant calls into `fulfillment-service`’s Membership adapter; the Lottery projection consumes the Fulfillment status event and only updates its local benefit state. Remove direct `MembershipProductMapper`/`MembershipService.purchase` coupling only after provider and caller contracts are verified.
- Modify `pay-service` only for the approved durable paid-membership event path and its outbox/listener tests. Do not make membership grants depend on a best-effort synchronous payment callback.
- Change Gateway/Nginx membership routing only in a separately reviewed cutover step after all gate and rollback evidence is complete. Do not alter login or RBAC routes.
- Update `docs/superpowers/handoffs/2026-09-24-marketing-service-decomposition-handoff.md` with evidence and the next unblocked gate; keep all audit reporting aggregate-only.

---

## Phase 0 — Close Decisions and Establish Baselines (no production writes)

### Task 1: Complete the identity/RBAC evidence gate

**Files:** No application files. Read/update only `docs/superpowers/audits/2026-09-24-marketing-identity-rbac-preflight-results.md` and the decomposition handoff after a qualified audit run.

- [ ] Verify an existing database account is explicitly confirmed SELECT-only and can read `luckyhub`, `hm_user`, and `information_schema`; do not test this with application credentials of unknown grants.
- [ ] If qualified, run `scripts/migration/marketing-identity-rbac-preflight.sql`; otherwise keep the report exactly in `未运行` state and state the concrete missing prerequisite.
- [ ] Record only the required totals/distributions and aggregate conflict/orphan counts. Never export or document per-row identity evidence.
- [ ] Stop this plan’s user-data work if identity ambiguity, projection anomalies, or RBAC orphans are nonzero; escalate the sanitized result for an explicit decision.
- [ ] Only after the identity audit has no blocking ambiguity, run `scripts/migration/marketing-membership-preflight.sql` with the same qualified access conditions; if membership/user/product or snapshot aggregates show anomalies, stop the corresponding Membership migration and record sanitized counts only.

**Acceptance:** The audit has a verified execution record and sanitized aggregates, or accurately remains unrun. It must never be represented as successful based on connectivity or static tests alone.

### Task 2: Approve Membership domain decisions

**Files:** Create `docs/superpowers/specs/2026-09-25-marketing-membership-boundary-design.md` before implementing contracts; update this plan only after the decisions are explicit.

The current file at this path is a review draft with recommended boundaries. It is not a business/security approval and does not unlock implementation.

- [ ] Approve separate commands for payment-backed purchase and Fulfillment-authorized reward grant, including authoritative source, order/product/user references, idempotency scope, replay conflict behavior, and failure/retry semantics.
- [ ] Decide `starts_at` meaning and define exact renewal, expired renewal, level change, and same-business-key replay behavior.
- [ ] Decide stable product reference semantics for Lottery snapshots and reward targets; include how old IDs are retained or resolved without a Git-tracked row map.
- [ ] Approve user-disable behavior and a replacement for the current `sys_user ... FOR UPDATE` coupling.
- [ ] Approve the new service’s `MEMBERSHIP_READ`/`MEMBERSHIP_MANAGE` enforcement path and public/admin compatibility behavior.

**Acceptance:** Each item has a specific approved behavior and testable contract; any unresolved item remains a hard stop rather than an implementation TODO.

### Task 3: Capture current API and caller behavior as executable baselines

**Files:** Existing Marketing tests and source under `marketing-service/src/main/java/com/dongqh/luckyhub/membership/`, `trade-service/src/main/java/com/dongqh/tradeservice/marketing/`, and `mall-backend/hm-service/src/main/java/com/hmall/integration/marketing/`.

- [ ] Record behavior for `GET /api/memberships/products`, `GET /api/memberships/me`, `POST /api/memberships/purchases`, and `POST /api/admin/membership-products`; explicitly mark the current purchase endpoint authorization gap rather than blessing it.
- [ ] Capture active/expired/no-membership result shape, discount basis points, order snapshot behavior, and consumer handling of HTTP/invalid responses.
- [ ] Identify real consumers from production call sites. Do not treat the unused Pay-side membership client class as an active dependency without a caller.
- [ ] Preserve and extend relevant baselines: `MembershipControllerTests`, `MembershipServiceTests`, `MembershipConcurrencyTests`, `RewardDefinitionServiceTests`, `RewardSnapshotServiceTests`, `LotteryRewardProjectionTests`, `FulfillmentWorkerTests`, `CashOrderServiceTests`, Trade Membership client/order tests, and `HttpMembershipBenefitClientTest`.

**Acceptance:** The expected compatibility behavior is asserted by tests, including current rejection/exception semantics; no route or runtime behavior changes in this task.

---

## Phase 1 — Build the Membership Provider (after all Phase 0 gates)

### Task 4: Add the isolated service module and schema migration

**Files:** `pom.xml`; `membership-service/pom.xml`; `membership-service/src/main/resources/application*.yaml`; `membership-service/src/main/resources/db/migration/V1__create_membership_tables.sql`.

- [ ] Follow existing sibling module dependency conventions and Java 17 settings; add only dependencies actually used.
- [ ] Add the three owned tables with reviewed column types, unique keys, foreign-key policy, idempotency constraint on grant business key, and indexes. No cross-service foreign keys.
- [ ] Keep service schema selection explicit (`hm_membership`) and externalize secrets. Do not add startup behavior that creates accounts or grants.
- [ ] Validate the Flyway migration against an isolated test database; do not point tests at Compose or a developer database.

**Acceptance:** The module builds in isolation with its required reactor dependencies; schema tests prove constraints and table ownership; no existing migration file changes.

### Task 5: Implement provider behavior behind approved contracts

**Files:** Membership controller/domain/mapper/service packages and provider tests under `membership-service/src/main/**` and `membership-service/src/test/**`.

- [ ] Implement product administration and product reads with the approved permission checks and current enabled-product ordering/limits.
- [ ] Implement active-benefit lookup preserving expiry filtering, response envelope, discount basis points, and the approved no-membership behavior.
- [ ] Implement paid-membership grant only from the verified Pay event contract, not from caller-supplied purchase identifiers alone.
- [ ] Implement reward membership grant as a separate, internal/idempotent operation with its own approved authority and key.
- [ ] Preserve business-key replay semantics: same key/same user/same product is idempotent; a conflicting user or product is rejected. Confirm resulting benefit response under replay in tests.
- [ ] Implement per-user serialization without querying or locking `sys_user`; apply the approved disable/race semantics.
- [ ] Implement the approved `starts_at`, renewal, expiry, and level-change rules and test boundary times.

**Acceptance:** Unit and isolated database tests cover success, expiry, races, duplicate/replayed events, conflicting replays, unavailable users per the approved contract, and permission failures. No provider code reads another service’s tables.

### Task 6: Add only concrete `hm-api` contracts

**Files:** `hm-api/src/main/java/com/dongqh/hmapi/client/` and `hm-api/src/main/java/com/dongqh/hmapi/dto/`, plus provider/caller contract tests.

- [ ] Add only DTOs and clients required by the simultaneously implemented provider and its real caller(s): benefit/product reads and the distinct grant operations as approved.
- [ ] Include stable event/command ID and explicit product/order domain references where required by the approved idempotency contract.
- [ ] Keep provider implementation independent from `hm-api` client classes; do not add speculative empty interfaces.
- [ ] Add serialization/contract tests for null/no-active-membership, error, expiry, and replay responses.

**Acceptance:** Provider and consumer agree on exact JSON fields, status/error behavior, authorization, and timeout/failure semantics. No DTO exposes a MyBatis entity.

---

## Phase 2 — Move Consumers Without Cross-Database Access

### Task 7: Move active-benefit reads to the Membership provider

**Files:** `trade-service/src/main/java/com/dongqh/tradeservice/marketing/` and tests; `mall-backend/hm-service/src/main/java/com/hmall/integration/marketing/` and tests; approved `hm-api` client/DTO files.

- [ ] Preserve `GET /api/memberships/me` compatibility for active/no-active users and retain the existing checkout fail-closed behavior for transport or invalid-response failures.
- [ ] Verify order creation snapshots the exact membership discount applied at quote time; later membership changes must not rewrite an existing order snapshot.
- [ ] Verify coupon applicability uses the same active-benefit/expiry rules as order creation.
- [ ] Keep old endpoint routing in place until the separately approved cutover; use explicit provider configuration for internal clients only after the new provider is deployable.

**Acceptance:** Existing consumer tests pass unchanged or with assertions that strengthen the same contract; no consumer queries `hm_membership` directly.

### Task 8: Replace Lottery/Fulfillment-to-Membership implementation coupling

**Files:** Existing Marketing `RewardDefinitionServiceImpl`, `RewardSnapshotServiceImpl`, `LotteryRewardProjectionServiceImpl`, `FulfillmentWorker`, Membership gateway/adapter and their tests, or their Lottery/Fulfillment service destinations when those slices are authorized; Membership product/grant API tests.

- [ ] Replace Lottery’s direct `MembershipProductMapper` reads with the approved provider product lookup.
- [ ] Preserve frozen reward snapshot values and prove the grant references the same product despite retries or product edits.
- [ ] Move asset issuance to Fulfillment’s Membership adapter; replace Lottery’s direct `MembershipService.purchase` call with status-event projection only.
- [ ] Preserve the invariant that Membership accepted the idempotent grant before Fulfillment reports success, and that Lottery advances its benefit projection only after the success event.
- [ ] Reconcile reward quantity: current Fulfillment membership request has no quantity while Lottery projection issues one grant per unit; approve and test the exact per-unit idempotency key and 64-character limit.
- [ ] Keep Lottery’s Redis/locking/quota/reconciliation implementation unchanged.

**Acceptance:** Reward definition, snapshot, fulfillment success, grant, and Lottery projection tests prove no direct Membership mapper access, no duplicate membership, and no premature “available” benefit state.

### Task 9: Connect verified payment success to paid Membership grants

**Files:** `pay-service` outbox/event producer and tests; approved Membership event consumer/tests; `hm-api` event DTO only if the approved contract uses it.

- [ ] Emit/consume only the approved durable payment-success event after Pay has committed authoritative success state.
- [ ] Carry a stable event key and distinguish Trade cash-purchase order references from Marketing’s historical string `MallOrder.orderNo`; do not assume their numeric/string forms are interchangeable.
- [ ] Make delivery at-least-once safe with Membership grant idempotency; preserve retry, dead-letter, and operational replay behavior.
- [ ] Reject events lacking the approved paid state, user identity, or stable product reference without issuing benefits.

**Acceptance:** Tests cover duplicate delivery, reordered delivery where relevant, transient Membership failure/retry, invalid/unpaid events, and conflicting reuse of an idempotency key. A direct public request cannot bypass payment verification.

---

## Phase 3 — Data Move and Compatibility Cutover (separate approval required)

### Task 10: Prepare and validate a controlled data migration

**Files:** Create `docs/superpowers/plans/2026-09-25-marketing-membership-data-migration-runbook.md` and a sanitized aggregate report; any row-level mapping stays in the approved protected execution environment and is not committed.

- [ ] Obtain explicit approval for the data migration window, backup/restore point, write freeze or controlled change capture, and rollback owner.
- [ ] Copy only Membership-owned data into `hm_membership`; preserve or explicitly resolve membership/product/grant identifiers according to the approved Lottery reference contract.
- [ ] Apply only an approved user-ID correspondence. If correspondence cannot be proven, stop; do not synthesize IDs, merge users, or write an account map into Git.
- [ ] Compare source/target table totals, status distributions, key uniqueness, referential aggregates, expiry/benefit aggregates, and idempotency records without exposing row-level identifiers.
- [ ] Keep Marketing as the only writer through rehearsal and verification. Do not dual-write.

**Acceptance:** A reviewed, repeatable runbook and sanitized parity evidence exist; rollback has been rehearsed or independently verified. No migration SQL is run by this plan until separately authorized.

### Task 11: Cut over compatible routes and retire the old writer

**Files:** Gateway membership route configuration and deployment configuration only in the approved cutover change; Marketing membership code removal only in a later cleanup change.

- [ ] Confirm provider health, permission enforcement, caller configuration, event consumer lag/retry, data parity, and rollback readiness.
- [ ] Switch membership routes/clients in one controlled cutover so exactly one writer remains active. Do not change login or RBAC routes.
- [ ] Verify public/admin contracts and all real consumer paths after cutover with approved non-production or production-safe checks.
- [ ] Observe the agreed rollback window. If rollback is needed, restore the old route/writer before accepting further writes in the new owner; reconcile only with an approved procedure.
- [ ] Remove old Marketing membership implementation only after verified zero traffic, no direct Mapper consumers, and explicit approval.

**Acceptance:** One owner, one write path, no cross-schema mapper references, successful contract/consumer checks, and documented rollback/cleanup evidence. Only then may the membership domain be marked extracted.

## Verification Commands and Scope

- Static identity-preflight contract: `python -B -m unittest tests.test_marketing_identity_rbac_preflight`. This does not connect to MySQL and does not satisfy the identity evidence gate by itself.
- New provider and dependent Java tests: run with the repository-supported Java 17 installation and a writable approved Maven cache, e.g. `mvn -pl membership-service -am test` plus the explicit Trade, HMall, reward, fulfillment, and payment suites from their modules. Do not point tests at Compose or any live database.
- Repository checks after each approved implementation slice: `git diff --check`; inspect `git status --short` to ensure unrelated/untracked user files remain untouched.
- No database execution, service startup, route change, code migration, or user-account change is part of the current plan-preparation task.
