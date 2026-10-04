# Marketing Membership Boundary Design (Review Draft)

Status: review draft. The compatibility-first extraction direction is approved at the architecture level; the business, payment, identity, and authorization decisions marked “approval required” below are not approved and block implementation/cutover.

## Purpose and scope

Extract Marketing’s `membership` domain into the planned `membership-service` without changing current read/discount behavior or allowing an unverified request to issue paid membership. Keep the current Marketing path intact until the new provider, data, consumers, and rollback procedure pass their gates. This document does not authorize database writes, account changes, route changes, or service startup.

The target service is one of the five planned replacements for `marketing-service`. The overall ten-business-service decomposition is not complete. `hm-security` remains a shared library, not an eleventh service.

## Current source behavior

- `GET /api/memberships/products` returns enabled products sorted by `price_cent`, then ID, capped at 100. Product administration currently creates products under `MEMBERSHIP_MANAGE`.
- `GET /api/memberships/me` returns the stored membership row, including an expired row if one exists. The Trade and HMall HTTP clients treat missing or expired membership as full price (`10,000` basis points); an invalid discount or failed/invalid service response throws instead of silently using full price. Trade snapshots the applied membership discount into the order.
- Marketing’s in-process `activeBenefit` filters by `expires_at > now`. Coupon checkout and Marketing cash-order pricing use this behavior.
- `POST /api/memberships/purchases` inherits class-level `MEMBERSHIP_READ`, accepts caller-supplied `businessNo` and product ID, and calls the grant logic without checking a paid Trade order. It is not a safe paid-purchase contract to copy into the new service.
- The same current `purchase` method is reused by Lottery reward projection. It first checks the grant business key, then locks an enabled `sys_user` row with `FOR UPDATE`, and then grants from the current enabled product. This couples a membership write transaction to Marketing’s identity database.
- Same-level active renewals extend from the current expiry. Otherwise the next period starts from the current time. Existing `user_membership.starts_at` is not rewritten on renewal or level change; `membership_grant_record.starts_at` stores the start of the individual grant period. Existing tests do not establish a broader public meaning for the account-level field.
- Lottery reward-definition and snapshot code reads `MembershipProductMapper` directly. The frozen reward payload carries product ID, code, level, duration, and quantity. After successful fulfillment, Lottery currently issues one grant per quantity with deterministic key `fulfillmentNo-M-index`, using numeric `rewardTargetId`; the parsed fulfillment payload’s code/duration do not drive that grant.
- Existing `PaySuccessMessage` has `eventId`, numeric `orderId`, `userId`, paid amount, coupon ID, and paid time. It does not identify an order domain or contain the Membership product reference/terms. It cannot by itself prove which Membership product should be issued when order IDs from different domains coexist.

## Recommended boundary

`membership-service` owns `membership_product`, `user_membership`, and `membership_grant_record` in `hm_membership`. It owns product administration, membership term calculation, benefit reads, and the idempotent recording/application of an authorized grant. It does not own user credentials, RBAC tables, Trade orders, Pay state, Lottery eligibility, or fulfillment status.

All communication across the boundary uses versioned HTTP/event contracts. No consumer reads the Membership schema or imports Membership mapper/entity classes. The current Marketing implementation remains the only writer until a separately approved cutover; no dual-write interval is allowed.

## Recommended flows

### Benefit reads and compatibility

1. Keep `GET /api/memberships/me` and `GET /api/memberships/products` response envelopes and fields compatible during transition.
2. Continue to represent the current membership row on `/me`; callers apply the existing expiry rule. Missing/expired membership remains full price; transport, malformed response, or invalid discount remains fail-closed.
3. Preserve Trade’s order-time discount snapshot. Do not recalculate historical orders from the customer’s current Membership state.
4. Keep existing admin permission semantics. The new provider uses the approved shared `hm-security`/`user-service` permission path; it does not copy Marketing RBAC data.
5. Do not change Gateway/Nginx login or RBAC routes. Membership route cutover is a separate final step after data parity and rollback readiness.

### Paid Membership purchase

Recommendation: the client starts a Membership purchase through `trade-service`, which creates the authoritative order and freezes the product/price terms. The membership controller must not turn a client-supplied business number into a grant. After Pay commits verified success, a durable Pay Outbox fact triggers the paid-grant path. Membership consumes it idempotently and applies the frozen purchase terms.

The current `PaySuccessMessage` is insufficient as the complete Membership purchase fact. Before implementation, Trade/Pay owners must approve one of these realizations:

- version a paid-order event to carry the immutable order domain/reference, stable event ID, user ID, Membership product reference and purchase terms needed to honor the order; or
- have the durable Pay event carry a stable order reference and have the Membership grant consumer retrieve an immutable, authenticated purchase snapshot from Trade, with retry and retention guarantees.

The first option is recommended because one durable event is self-contained and replayable, but it requires Trade to put the Membership order/product snapshot into the payment handoff and requires Pay to publish that snapshot with its verified-success Outbox fact. Do not silently extend the existing generic event or infer product identity from a bare numeric `orderId`.

Approval required before implementation: event owner/version, order-domain discriminator, product ID/code/term snapshot, authoritative paid amount and currency/unit, behavior for late payment after product deactivation, cancellation/refund/chargeback handling, event replay/rejection semantics, and the source of a globally unambiguous grant business key. The grant key must fit the target column and must not collide with existing Lottery/legacy grant keys.

The current public `POST /api/memberships/purchases` is not exposed by the new service as a paid grant endpoint. Its legacy route/behavior remains untouched in this preparation task; its eventual deprecation/replacement must be included in an approved compatibility/cutover change.

### Lottery Membership reward

Recommendation aligned with the decomposition design: Lottery owns reward eligibility and the immutable draw snapshot; `fulfillment-service` owns the grant task and invokes the Membership reward-grant contract; Membership applies the idempotent asset grant; Fulfillment publishes terminal status through its Outbox; Lottery consumes that status and updates only its `user_benefit` projection. Lottery must not call Membership to issue the asset after Fulfillment has already succeeded.

The Membership grant must succeed before Fulfillment records terminal success. If Membership accepted a grant but Fulfillment failed before committing success, retry the same Membership idempotency key. Lottery marks the benefit available only after the success event. A failure, quarantine, or timeout does not create a second grant and does not make the benefit available.

The immutable grant reference includes product identity and award quantity. Preserve `fulfillmentNo-M-index` only if source data and length/collision checks prove that it is stable and globally unique within the existing 64-character business-key limit. Current `MembershipGrantRequest` contains fulfillment number, user, code, and duration but no product ID or quantity; current Lottery projection loops per quantity using `rewardTargetId`. The split must reconcile these contracts explicitly so a quantity greater than one is neither dropped nor double-granted. Include product ID and code in the approved contract and verify that they identify the same product. A mismatch or unknown product follows the approved retry/quarantine behavior rather than guessing from a changed catalog row.

Approval required before implementation: whether awarded benefits honor frozen level/duration at draw time or catalog terms at grant time; how the Membership provider receives/validates product reference and quantity; exact event/command authority; reward-key length/namespace; and the failure/replay contract between Membership acknowledgment, Fulfillment success, and Lottery projection status.

### User identity, concurrency, and permissions

Recommendation: Membership uses the trusted user ID supplied by the approved shared security path and never reads or locks `sys_user`. User mapping is established only by the identity/RBAC preflight and approved migration plan. Any identity ambiguity, projection anomaly, or RBAC orphan pauses all user-keyed data migration.

The current `sys_user ... FOR UPDATE` lock cannot be replaced by a remote status lookup while claiming equivalent atomicity. Before writes move, the user/security owner must approve whether grant eligibility is frozen when Trade/Lottery authorizes the source operation or whether a separate serialized revocation/suspension event is required. Also decide whether an already-paid order must grant if the user is disabled before Pay event consumption. Until decided, no new cross-service concurrency behavior is considered approved.

Public read/admin permissions retain `MEMBERSHIP_READ` and `MEMBERSHIP_MANAGE` semantics. Internal paid/reward grant endpoints must use an approved service-to-service trust mechanism and must not inherit end-user `MEMBERSHIP_READ` as their authorization.

## Data identity and migration

- The target schema is the existing design’s `hm_membership`; no new MySQL cluster is required.
- Run identity/RBAC and Membership preflight SQL only with an existing confirmed SELECT-only account, a usable MySQL client, and verified access to `luckyhub`, `hm_user`, and `information_schema`. Both current reports remain `未运行` until those conditions are true.
- `marketing-membership-preflight.sql` checks required table metadata, product/member/grant aggregates, Marketing-user/product orphan counts, and Lottery reward/snapshot reference mismatches. Its `hm_user.user` check is metadata-only; it does not prove cross-system identity correspondence.
- If preflight evidence is clean and owners approve the migration, preserve existing Membership product IDs where possible because Lottery stores numeric targets. Any necessary correspondence is resolved only in the protected migration environment; no row-level user/product mapping is written to Git or handoff docs.
- Copy the existing account-level `starts_at` unchanged for compatibility until product owners approve a new meaning. Treat each grant-record interval as the auditable individual grant period. Do not rewrite old Marketing V1–V28 migrations.
- Rehearse aggregate parity, replay behavior, writer freeze, rollback, and post-cutover grant/event reconciliation. Do not dual-write. A route rollback after the new service accepts writes must not discard those accepted grants; use only an approved reconciliation/replay runbook.

## Failure and consistency rules

- No active membership means full price under the current consumer contract. A failed membership read, invalid response, or invalid discount is not treated as “no membership”.
- Paid grants originate only from verified durable payment facts; reward grants are authorized only as an idempotent Fulfillment operation that must succeed before Fulfillment publishes its terminal success fact. A client-authored purchase request is never evidence of payment or fulfillment.
- Duplicate delivery of the same logical grant is idempotent. Reuse of a grant key with a different user or product is a conflict and must not extend membership.
- Membership grant and Lottery projection remain separate service transactions. Retry from durable event/command identity; do not rely on a cross-database transaction or direct mapper call.
- Product disablement, refund/reversal, event timeout, dead-letter replay, and user suspension are explicit contract outcomes, not silent full-price fallbacks.

## Verification required before cutover

- Provider contract tests for no membership, expired membership, invalid benefit values, API envelope, permission failures, and current caller fail-closed behavior.
- Membership tests for same-level renewal, expired renewal, level change, exact `starts_at` compatibility, concurrent grants, duplicate delivery, conflicting replay, and authorized user state per the approved decision.
- Pay tests proving only verified committed success produces the durable paid grant fact; duplicate delivery and consumer retry do not duplicate membership.
- Lottery/Fulfillment tests proving the reward snapshot, fulfillment status, Membership grant, and Lottery projection agree across retries and product-reference mismatch.
- Trade/HMall tests proving order-time discount snapshots and full-price behavior are unchanged.
- Sanitized source/target aggregate parity and an approved rollback/reconciliation rehearsal.

## Decisions still requiring domain-owner approval

1. Paid order event ownership and the immutable purchase snapshot contract, including refunds/reversals.
2. Reward terms at draw time versus grant time, reward quantity handling, and the stable globally unique grant-key format/length.
3. Disabled-user behavior for already-authorized but delayed paid/reward events; replacement for the cross-service user lock.
4. Account-level `starts_at` public meaning, while retaining current stored values until decided.
5. Permission enforcement and migration order after identity/RBAC aggregate audit.

No code, database, account, route, or service state was changed to produce this review draft.
