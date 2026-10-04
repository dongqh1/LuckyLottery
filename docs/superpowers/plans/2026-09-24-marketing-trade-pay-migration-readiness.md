# Marketing Trade/Pay Migration Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a statically tested, aggregate-only preflight and decision package for moving Marketing cash orders and payment attempts toward `trade-service`/`pay-service`, without changing runtime behavior or data.

**Architecture:** First inspect only redacted aggregates and target schema shape using explicit-schema `SELECT` statements; never infer a row mapping from overlapping numeric IDs. Keep the existing Marketing write path intact. Stop at a decision gate for payment-attempt storage, event compatibility, and late-success handling; a separate service-code migration plan follows only after audit evidence and those decisions are approved.

**Tech Stack:** Python `unittest`, MySQL 8.4 read-only SQL, Markdown. Existing source-of-truth contracts are in `hm-api`, `trade-service`, `pay-service`, and the Marketing V13 migration.

## Global Constraints

- Preserve the user's existing uncommitted changes and all untracked files.
- Do not create or modify database accounts; do not use application credentials whose grants are unknown.
- Execute SQL only with an already confirmed SELECT-only account that can read `luckyhub`, `hm_trade`, `hm_pay`, and `information_schema`.
- Do not start Compose, schema-init, or any stateful service. In particular, do not execute `mysql/migrations/V007__create_trade_service_database.sql` or `V008__create_pay_service_database.sql`; they create/alter accounts and grants.
- Keep the new SQL explicit-schema and aggregate-only. Do not output row-level order/payment references, user IDs, names, phones, credentials, signatures, or payment payloads.
- Keep Marketing as the sole old-flow writer until a separately approved cutover; no code/data migration or Gateway/Nginx route changes in this readiness plan.
- Do not begin user-service identity/RBAC migration, alter Marketing Flyway V1–V28, or change lottery Redis Lua, quota keys, Redisson locks, TTL, or reconciliation behavior.
- Never interpret an unrun query, missing credential, or unreadable schema as zero rows or a clean mapping.
- Do not stage or commit changes in this workspace; `.git` index writes were denied by the current filesystem permissions.

## File Map

- Create `tests/test_marketing_trade_pay_preflight.py` to enforce the SQL safety and aggregate-output contract without a database connection.
- Create `scripts/migration/marketing-trade-pay-preflight.sql` to report only MySQL version, table/status totals, amount aggregates, attempt multiplicity/orphan counts, and target table/status aggregates.
- Create `docs/superpowers/audits/2026-09-24-marketing-trade-pay-preflight-results.md` to record the actual run state and only sanitized aggregate results.
- Modify `docs/superpowers/specs/2026-09-24-marketing-trade-pay-boundary-design.md` only when review resolves an explicit architecture decision; no implementation choice is silently inferred by this plan.
- Modify `docs/superpowers/handoffs/2026-09-24-marketing-service-decomposition-handoff.md` to record the true run state, validation, open decisions, and next gate.

---

### Task 1: Add a failing static safety contract for the payment preflight SQL

**Files:**
- Create: `tests/test_marketing_trade_pay_preflight.py`
- Create later in Task 2: `scripts/migration/marketing-trade-pay-preflight.sql`

**Interfaces:**
- Produces: `python -B -m unittest tests.test_marketing_trade_pay_preflight` verifies the preflight SQL is read-only, explicit-schema, aggregate-only, and contains the required result aliases.
- Does not connect to MySQL or read credentials.

- [x] **Step 1: Write the failing contract test**

```python
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
SQL_PATH = ROOT / "scripts/migration/marketing-trade-pay-preflight.sql"


class MarketingTradePayPreflightTest(unittest.TestCase):
    def read_sql(self) -> str:
        return SQL_PATH.read_text(encoding="utf-8")

    def statements(self) -> list[str]:
        sql = self.read_sql()
        sql = re.sub(r"/\*.*?\*/|--[^\n]*|#[^\n]*", "", sql, flags=re.S)
        return [statement.strip() for statement in sql.split(";") if statement.strip()]

    def test_every_statement_is_a_read_only_select(self):
        statements = self.statements()
        self.assertGreaterEqual(len(statements), 8)
        forbidden = re.compile(
            r"\b(INSERT|UPDATE|DELETE|REPLACE|CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|"
            r"CALL|PREPARE|EXECUTE|LOCK\s+TABLES|INTO\s+(OUTFILE|DUMPFILE)|FOR\s+UPDATE)\b",
            re.IGNORECASE,
        )
        for statement in statements:
            self.assertRegex(statement, r"(?is)^SELECT\b")
            self.assertIsNone(forbidden.search(statement), statement)

    def test_queries_use_explicit_source_and_target_schemas(self):
        sql = self.read_sql().lower()
        for table in (
            "luckyhub.mall_order",
            "luckyhub.payment_order",
            "hm_trade.",
            "hm_pay.pay_order",
            "hm_pay.pay_message_outbox",
            "information_schema.",
        ):
            self.assertIn(table, sql)

    def test_queries_never_select_row_level_payment_or_personal_fields(self):
        sql = self.read_sql()
        self.assertNotRegex(sql, r"(?is)\bSELECT\s+\*")
        for field in ("username", "phone", "mobile", "password", "credential", "signature"):
            self.assertNotRegex(sql, rf"(?i)\b{field}\b")
        self.assertNotRegex(sql, r"(?is)SELECT\s+(?:p\.)?payment_no\b")
        self.assertNotRegex(sql, r"(?is)SELECT\s+(?:o\.)?order_no\b")

    def test_required_aggregate_result_aliases_and_server_version_exist(self):
        sql = self.read_sql().lower()
        for alias in (
            "mysql_server_version",
            "marketing_order_total",
            "marketing_payment_total",
            "orders_with_multiple_payment_attempts",
            "orphan_payment_count",
            "payment_amount_mismatch_count",
            "trade_order_total",
            "pay_order_total",
        ):
            self.assertIn(alias, sql)
        self.assertRegex(sql, r"(?is)select\s+version\(\)\s+as\s+mysql_server_version")


if __name__ == "__main__":
    unittest.main()
```

- [x] **Step 2: Run the test and verify it fails only because the SQL is absent**

Run: `python -B -m unittest tests.test_marketing_trade_pay_preflight -v`

Expected: FAIL with `FileNotFoundError` for `scripts/migration/marketing-trade-pay-preflight.sql`; no database connection is attempted.

---

### Task 2: Add the explicit-schema aggregate-only SQL

**Files:**
- Create: `scripts/migration/marketing-trade-pay-preflight.sql`
- Test: `tests/test_marketing_trade_pay_preflight.py`

**Interfaces:**
- Consumes: SELECT visibility into `luckyhub`, `hm_trade`, `hm_pay`, and `information_schema`.
- Produces: aggregate result sets only; source business keys are used only inside grouped/join predicates and are never returned as rows.

- [x] **Step 1: Add the server version and schema/table inventory queries**

```sql
SELECT VERSION() AS mysql_server_version;

SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema IN ('luckyhub', 'hm_trade', 'hm_pay')
  AND table_name IN (
    'mall_order', 'payment_order', 'order', 'pay_order', 'pay_message_outbox'
  )
ORDER BY table_schema, table_name;
```

The inventory query returns schema object names only. If a required table is absent or unreadable, stop the SQL run and record that condition; do not run Compose or schema initialization to create it.

- [x] **Step 2: Add Marketing order and payment totals/status/amount aggregates**

```sql
SELECT COUNT(*) AS marketing_order_total
FROM luckyhub.mall_order;

SELECT status, COUNT(*) AS row_count
FROM luckyhub.mall_order
GROUP BY status
ORDER BY status;

SELECT COUNT(*) AS marketing_payment_total,
       COALESCE(SUM(amount_cent), 0) AS amount_cent_sum,
       MIN(amount_cent) AS amount_cent_min,
       MAX(amount_cent) AS amount_cent_max
FROM luckyhub.payment_order;

SELECT status, callback_result, COUNT(*) AS row_count,
       COALESCE(SUM(amount_cent), 0) AS amount_cent_sum
FROM luckyhub.payment_order
GROUP BY status, callback_result
ORDER BY status, callback_result;
```

- [x] **Step 3: Add attempt-multiplicity and source referential aggregates**

```sql
SELECT COUNT(*) AS orders_with_multiple_payment_attempts,
       COALESCE(MAX(payment_attempt_count), 0) AS max_payment_attempts_for_one_order
FROM (
    SELECT COUNT(*) AS payment_attempt_count
    FROM luckyhub.payment_order
    GROUP BY order_no
    HAVING COUNT(*) > 1
) attempts;

SELECT (SELECT COUNT(*)
        FROM luckyhub.payment_order p
        LEFT JOIN luckyhub.mall_order o ON o.order_no = p.order_no
        WHERE o.id IS NULL) AS orphan_payment_count,
       (SELECT COUNT(*)
        FROM luckyhub.mall_order o
        LEFT JOIN luckyhub.payment_order p ON p.order_no = o.order_no
        WHERE p.id IS NULL) AS orders_without_payment_count,
       (SELECT COUNT(*)
        FROM luckyhub.payment_order p
        JOIN luckyhub.mall_order o ON o.order_no = p.order_no
        WHERE p.amount_cent <> o.payable_amount_cent) AS payment_amount_mismatch_count;
```

- [x] **Step 4: Add target trade/pay totals, status, amount, and Outbox aggregates**

```sql
SELECT COUNT(*) AS trade_order_total,
       MIN(total_fee) AS total_fee_min,
       MAX(total_fee) AS total_fee_max
FROM hm_trade.`order`;

SELECT status, COUNT(*) AS row_count,
       COALESCE(SUM(total_fee), 0) AS total_fee_sum
FROM hm_trade.`order`
GROUP BY status
ORDER BY status;

SELECT COUNT(*) AS pay_order_total,
       MIN(amount) AS amount_min,
       MAX(amount) AS amount_max
FROM hm_pay.pay_order;

SELECT status, COUNT(*) AS row_count,
       COALESCE(SUM(amount), 0) AS amount_sum
FROM hm_pay.pay_order
GROUP BY status
ORDER BY status;

SELECT event_type, status, COUNT(*) AS row_count
FROM hm_pay.pay_message_outbox
GROUP BY event_type, status
ORDER BY event_type, status;
```

The Marketing `order_no` and existing `hm_trade.order.id`/`hm_pay.pay_order.biz_order_no` are not joined by numeric coincidence. The result documents target/source aggregates only; it does not establish an ID mapping.

- [x] **Step 5: Run the static contract test and verify it passes**

Run: `python -B -m unittest tests.test_marketing_trade_pay_preflight -v`

Expected: all four static tests pass; no MySQL client is invoked.

---

### Task 3: Record the payment preflight as unrun unless every access gate is met

**Files:**
- Create: `docs/superpowers/audits/2026-09-24-marketing-trade-pay-preflight-results.md`
- Modify: `docs/superpowers/handoffs/2026-09-24-marketing-service-decomposition-handoff.md`

**Interfaces:**
- Produces: a short report with run status, execution date/server version only if collected, approved aggregate fields, and unresolved decisions.
- Never stores payment numbers, order numbers, user IDs, usernames, phones, credentials, signatures, or row-level joins.

- [x] **Step 1: Record the initial report state**

Use these exact facts until a permitted run occurs: `SQL 状态：未运行`; `MySQL 服务端版本：未采集`; all counts/amount aggregates: `未采集`; reason: `没有已确认仅有 SELECT 权限且可读 luckyhub、hm_trade、hm_pay、information_schema 的审计账号；目标 schema 可读性未验证`.

- [x] **Step 2: Run the required local checks**

Run: `python -B -m unittest tests.test_marketing_trade_pay_preflight -v`

Expected: all four static checks pass.

Run: `python -B -m unittest tests.test_marketing_identity_rbac_preflight`

Expected: 4 tests pass; this checks the existing identity preflight contract and does not execute its SQL.

Run: `git diff --check`

Expected: exit code 0. Manually check the new untracked SQL/report files for trailing whitespace because ordinary `git diff --check` may not include untracked files.

---

### Task 4: Execute the SQL only under an independently confirmed read-only account

**Files:**
- Read: `scripts/migration/marketing-trade-pay-preflight.sql`
- Modify only aggregate fields in: `docs/superpowers/audits/2026-09-24-marketing-trade-pay-preflight-results.md`

**Interfaces:**
- Consumes: an already existing credential, confirmed by the database owner to have only SELECT privileges and access to all four required schemas.
- Produces: no row-level output retained in Git; if the credential or schema access is unavailable, the report remains `未运行` with the exact blocker.

- [x] **Step 1: Check the execution gate without probing unknown credentials**

Proceed only if a database owner has already confirmed the account is SELECT-only and the same account can read `luckyhub`, `hm_trade`, `hm_pay`, and `information_schema`. A MySQL client or running Docker database alone is insufficient.

- [x] **Step 2: Stop safely when the gate is not met**

Do not try application credentials, do not create an audit user, do not inspect unknown grants by logging in, do not start Compose/schema-init, and do not run the SQL. Keep every result `未采集`; record only which prerequisite is absent.

- [ ] **Step 3: Run the SQL with the approved credential mechanism when the gate is met**

Use the approved local credential mechanism without placing secrets in the command line, terminal output, environment dump, Git, or report. Run only the SQL file above. Retain only its version and aggregate result sets for the report; discard any transient client transcript after verifying it contains only aggregate output.

- [ ] **Step 4: Stop migration planning on any mismatch or ambiguity**

If the query shows duplicate attempts, orphan payments/orders, amount mismatch, unrecognized status values, target range risk, or schema access/table gaps, record only aggregate counts and pause. Do not select a mapping rule from row-level data, rewrite IDs, merge records, alter schema, or move data.

---

### Task 5: Resolve the implementation decision gate before writing service code

**Files:**
- Review: `docs/superpowers/specs/2026-09-24-marketing-trade-pay-boundary-design.md`
- Update only after explicit approval: the design spec and handoff.

**Interfaces:**
- Consumes: the sanitized aggregate report and the reviewed boundary design.
- Produces: explicit approved decisions for payment-attempt representation, string business-key compatibility, status/amount mapping, Outbox versus Seata responsibilities, timeout/late-success handling, and legacy response compatibility.

- [ ] **Step 1: Compare the two documented success-consistency options**

Use the source behavior and existing `PayOutboxService`/`PayStatusListener` tests to document callback response timing, order-status visibility, coupon/inventory effects, retries, and failure recovery for both synchronous Seata coordination and Outbox/state-machine processing.

- [ ] **Step 2: Record the selected payment-attempt contract**

The chosen contract must preserve every legacy `payment_no` attempt for an order, define the idempotency key, and specify which attempt is returned by status queries. It must not assume the current unique `hm_pay.pay_order.biz_order_no` row can represent multiple Marketing attempts.

- [ ] **Step 3: Record explicit failure and compatibility behavior**

Define the allowed status mapping; overflow handling for Marketing `BIGINT amount_cent` versus target `INT amount`; timeout cancellation behavior; late-success quarantine/retry/compensation; and the exact old endpoint DTO/error behavior. No automatic refund, ID rewrite, account merge, or permission copy is introduced by this plan.

- [ ] **Step 4: Stop at the approval gate and create a separate code-level migration plan**

Do not implement the trade/pay service migration under this readiness plan. After audit evidence and all decisions are approved, create a new service-code/data-cutover plan with exact DTOs, schema migration files, owner APIs, consumer queues, test methods, rollback operations, and separately approved route/data execution gates.

---

## Completion Boundary

This plan completes when the aggregate-only SQL and static tests are ready, the report truthfully records `未运行` or sanitized results, and implementation decisions are either approved or explicitly pending user action. Completion does not mean `trade-service` or `pay-service` has absorbed Marketing code/data, and does not mean Marketing has been split into ten business microservices.
