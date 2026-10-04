# Marketing Catalog and Channel Inventory Preflight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish sanitized, read-only identity/RBAC and catalog/inventory audit evidence, then stop at explicit mapping and authorization gates before planning any data migration or route cutover.

**Architecture:** Reuse the existing identity/RBAC preflight and add a separate aggregate-only catalog/inventory SQL preflight. The scripts require no application startup, emit no row-level account or catalog mapping, and run only with a verified SELECT-only audit account; unresolved product/SKU mapping or service-side authorization prevents migration implementation and cutover.

**Tech Stack:** Python 3 standard-library `unittest`, MySQL 8-compatible read-only SQL, Markdown audit/handoff documents, PowerShell 7.

## Global Constraints

- Marketing V1–V28 are immutable; any eventual target schema uses item-service-owned migrations.
- Only a confirmed SELECT-only database account may run audit SQL; required schemas and `information_schema` must be readable.
- Do not create or modify database accounts, use an application account with unknown grants, start Compose, or start any stateful service.
- Store only aggregate, sanitized results in Git; never store row-level user data, secrets, or product/SKU-to-item mappings.
- Do not infer that Marketing SKU IDs equal `hmall.item.id`; do not infer mappings from names or SKU codes.
- Do not migrate identity/RBAC data, change Gateway/Nginx login or RBAC routes, or expose item-service admin routes without verified service-side authorization.
- If identity ambiguity, product mapping ambiguity, projection anomaly, or inventory imbalance is found, record it and stop; do not edit IDs, merge records, or copy permissions.
- Preserve the distinct lottery prize inventory and all lottery Redis Lua, quota-key, Redisson lock, TTL, and reconciliation behavior.

---

## Repository Map

- `scripts/migration/marketing-identity-rbac-preflight.sql`: existing SELECT-only identity/RBAC preflight.
- `tests/test_marketing_identity_rbac_preflight.py`: existing static safety and query-contract test for that SQL.
- `docs/superpowers/audits/2026-09-24-marketing-identity-rbac-preflight-results.md`: sanitized identity/RBAC result and not-run reason.
- `docs/superpowers/handoffs/2026-09-24-marketing-service-decomposition-handoff.md`: current decomposition status and next gate.
- `marketing-service/src/main/resources/db/migration/V8__add_catalog_and_reward_foundation.sql`: Marketing `product` and `product_sku` schema; do not edit.
- `marketing-service/src/main/resources/db/migration/V9__add_channel_inventory.sql`: Marketing channel inventory schema; do not edit.
- `marketing-service/src/main/resources/db/migration/V18__bind_inventory_ledger_to_hmall_item.sql`: ledger identity alternatives; do not edit.
- `marketing-service/src/main/java/com/dongqh/luckyhub/catalog/service/CatalogService.java`: current product lookup, page, and purchase/redemption snapshots.
- `marketing-service/src/main/java/com/dongqh/luckyhub/inventory/channel/service/ChannelInventoryService.java`: current inventory command boundary.
- `item-service/src/main/java/com/dongqh/itemservice/domain/po/Item.java`: existing HMall item representation.
- `hm-security/src/main/java/com/dongqh/hmsecurity/context/TrustedUserContext.java`: shared trusted-user context only; it does not currently provide permission lookup/interception.

## Task 1: Re-run the Requested Identity/RBAC Preflight Test and Record Its Actual State

**Files:**
- Read: `tests/test_marketing_identity_rbac_preflight.py`
- Read: `scripts/migration/marketing-identity-rbac-preflight.sql`
- Modify: `docs/superpowers/audits/2026-09-24-marketing-identity-rbac-preflight-results.md`
- Modify: `docs/superpowers/handoffs/2026-09-24-marketing-service-decomposition-handoff.md`

- [x] Run the required static test from the repository root:

```powershell
python -B -m unittest tests.test_marketing_identity_rbac_preflight
```

Expected: exit code `0`; the test result must be copied accurately into the handoff.

- [x] Check whether a MySQL client is already available and whether an already-provisioned audit credential is explicitly confirmed SELECT-only for `luckyhub`, `hm_user`, and `information_schema`. The client is available; no confirmed audit credential was found. Do not print or save the credential. Do not create an account, change grants, use application credentials, or start a database/service to make the check pass.

- [ ] If and only if all three prerequisites hold, execute `scripts/migration/marketing-identity-rbac-preflight.sql` with that audit credential. Save only the aggregates requested by the existing audit report. Keep any raw conflict rows in the controlled database client session; do not redirect or copy them into files.

- [x] If any prerequisite is absent or the database is unreachable, leave the SQL status as `未运行` and state the exact missing prerequisite. Do not describe a static test as a database audit.

- [x] Run `git diff --check` and verify the audit report contains no row-level usernames, phone numbers, password hashes, credentials, or account mappings.

## Task 2: Add a Read-Only Aggregate Catalog/Inventory Preflight

**Files:**
- Create: `scripts/migration/marketing-catalog-inventory-preflight.sql`
- Create: `tests/test_marketing_catalog_inventory_preflight.py`

**Interfaces:**
- Consumes: read access to `luckyhub.product`, `luckyhub.product_sku`, `luckyhub.sku_inventory`, `luckyhub.inventory_channel_stock`, `luckyhub.inventory_reservation`, `luckyhub.inventory_ledger`, `hmall.item`, and `information_schema.columns`.
- Produces: aggregate result sets only: product/SKU status counts, sale-mode counts, direct mapping-column count, ledger identity-shape/orphan counts, reservation status counts, and inventory-balance anomaly counts.

- [x] Write `tests/test_marketing_catalog_inventory_preflight.py` first. Use the following test implementation so every SQL statement is checked before any database execution:

```python
import re
import unittest
from pathlib import Path


SQL_PATH = (
    Path(__file__).resolve().parents[1]
    / "scripts"
    / "migration"
    / "marketing-catalog-inventory-preflight.sql"
)


class MarketingCatalogInventoryPreflightTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = SQL_PATH.read_text(encoding="utf-8")
        cls.sql_without_comments = re.sub(
            r"/\*.*?\*/|--[^\r\n]*|\#[^\r\n]*",
            "",
            cls.sql,
            flags=re.DOTALL,
        )
        cls.statements = [
            statement.strip()
            for statement in cls.sql_without_comments.split(";")
            if statement.strip()
        ]

    def test_script_contains_only_select_statements(self):
        self.assertTrue(self.statements)
        for statement in self.statements:
            self.assertRegex(statement, r"(?is)^SELECT\b")
            self.assertNotRegex(
                statement,
                r"(?i)\b(ALTER|CREATE|DELETE|DROP|GRANT|INSERT|RENAME|REPLACE|REVOKE|TRUNCATE|UPDATE|USE)\b",
            )

    def test_script_never_selects_all_columns_or_row_level_mapping(self):
        self.assertNotRegex(self.sql_without_comments, r"(?i)\bSELECT\s+\*")
        self.assertNotRegex(
            self.sql_without_comments,
            r"(?i)\b(product_name|sku_name|sku_code|business_no|username|phone|password_hash)\b",
        )

    def test_script_reports_required_aggregate_domains(self):
        for token in (
            "luckyhub.product",
            "luckyhub.product_sku",
            "luckyhub.sku_inventory",
            "luckyhub.inventory_channel_stock",
            "luckyhub.inventory_reservation",
            "luckyhub.inventory_ledger",
            "hmall.item",
            "information_schema.columns",
            "COUNT(*)",
        ):
            with self.subTest(token=token):
                self.assertIn(token, self.sql_without_comments)


if __name__ == "__main__":
    unittest.main()
```

- [x] Run the new test before creating the SQL file:

```powershell
python -B -m unittest tests.test_marketing_catalog_inventory_preflight
```

Expected: FAIL because the SQL file does not exist yet.

- [x] Create `scripts/migration/marketing-catalog-inventory-preflight.sql` with only fully qualified aggregate SELECTs. Use these result contracts and no row-level columns:

```sql
-- Product and SKU status distribution.
SELECT 'product_status' AS metric, CAST(status AS CHAR) AS dimension, COUNT(*) AS row_count
FROM luckyhub.product
GROUP BY status
UNION ALL
SELECT 'product_sku_status', CAST(status AS CHAR), COUNT(*)
FROM luckyhub.product_sku
GROUP BY status;

-- Whether product_sku contains an explicit HMall item reference; do not infer one by matching text.
SELECT 'product_sku_direct_item_link_columns' AS metric,
       COUNT(*) AS aggregate_count
FROM information_schema.columns
WHERE table_schema = 'luckyhub'
  AND table_name = 'product_sku'
  AND column_name IN ('item_id', 'hmall_item_id');

-- Catalog sales-mode distribution, without returning price or SKU values.
SELECT 'product_sku_sale_mode' AS metric,
       CONCAT(cash_enabled, ':', points_enabled) AS dimension,
       COUNT(*) AS row_count
FROM luckyhub.product_sku
GROUP BY cash_enabled, points_enabled;

-- Ledger identity shape and aggregate orphan counts.
SELECT 'inventory_ledger_identity_shape' AS metric,
       CASE
           WHEN sku_id IS NOT NULL AND hmall_item_id IS NULL THEN 'marketing_sku_only'
           WHEN sku_id IS NULL AND hmall_item_id IS NOT NULL THEN 'hmall_item_only'
           WHEN sku_id IS NULL AND hmall_item_id IS NULL THEN 'both_null'
           ELSE 'both_present'
       END AS dimension,
       COUNT(*) AS row_count
FROM luckyhub.inventory_ledger
GROUP BY dimension;

SELECT 'inventory_ledger_missing_marketing_sku' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.inventory_ledger ledger
LEFT JOIN luckyhub.product_sku sku ON sku.id = ledger.sku_id
WHERE ledger.sku_id IS NOT NULL AND sku.id IS NULL;

SELECT 'inventory_ledger_missing_hmall_item' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.inventory_ledger ledger
LEFT JOIN hmall.item item ON item.id = ledger.hmall_item_id
WHERE ledger.hmall_item_id IS NOT NULL AND item.id IS NULL;

-- Reservation states and stored stock invariant anomalies.
SELECT 'inventory_reservation_status' AS metric, status AS dimension, COUNT(*) AS row_count
FROM luckyhub.inventory_reservation
GROUP BY status;

SELECT 'sku_inventory_allocation_exceeds_total' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.sku_inventory
WHERE allocated_stock > total_stock;

SELECT 'channel_stock_balance_mismatch' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.inventory_channel_stock
WHERE allocated_stock <> available_stock + reserved_stock + consumed_stock;

-- Target item population is an aggregate only; it does not assert a Marketing SKU mapping.
SELECT 'hmall_item_total' AS metric, COUNT(*) AS aggregate_count
FROM hmall.item;
```

- [x] Run the new unit test again:

```powershell
python -B -m unittest tests.test_marketing_catalog_inventory_preflight
```

Expected: PASS. If it fails, fix only the static test/query contract before proceeding; do not run the SQL.

## Task 3: Execute the Catalog/Inventory SQL Only Under the Read-Only Gate

**Files:**
- Read: `scripts/migration/marketing-catalog-inventory-preflight.sql`
- Create: `docs/superpowers/audits/2026-09-24-marketing-catalog-inventory-preflight-results.md`
- Modify: `docs/superpowers/handoffs/2026-09-24-marketing-service-decomposition-handoff.md`

- [x] Check the MySQL client and read-only-account gate: the client is available, but no account is confirmed SELECT-only for `luckyhub`, `hmall`, `hm_user`, and `information_schema`. The identity/RBAC SQL separately requires `hm_user` readability as specified in Task 1.

- [ ] Execute the catalog/inventory SQL only after the prior step succeeds. Do not start Compose or another stateful process to obtain access. Do not use Marketing or HMall application credentials unless their grants have independently been confirmed SELECT-only.

- [ ] Copy only the result-set aggregates, MySQL version, execution date, and blockers into the audit report. Record direct mapping as “not structurally available” when the mapping-column aggregate is zero; do not create an inferred crosswalk from product names, SKU codes, or coincidental IDs.

- [ ] Keep row-level data in the controlled SQL session only. Do not save raw query output, credentials, or row mappings to a file, terminal transcript, Git, or handoff.

- [x] If any required connection condition is absent, keep the report status `未运行`, list the precise missing condition, and stop without starting services.

## Task 4: Stop at the Mapping and Authorization Decision Gate

**Files:**
- Modify only if supported by verified aggregate evidence: `docs/superpowers/specs/2026-09-24-item-service-catalog-inventory-decomposition-design.md`
- Modify: `docs/superpowers/handoffs/2026-09-24-marketing-service-decomposition-handoff.md`

- [ ] Compare the audit aggregates with the approved design. Confirm whether every Marketing SKU has an explicit, business-approved relation to an HMall item and whether that relation is unique enough for the selected ownership model.

- [ ] Confirm the item-service schema migration mechanism against the existing non-empty HMall database before adding automatic Flyway startup behavior. `item-service` currently has no Flyway dependency or migration directory, so do not assume an automatic baseline is safe.

- [ ] Confirm where `catalog:read`, `catalog:manage`, and `inventory:manage` are enforced at the service boundary. `TrustedUserContext` alone is not permission enforcement, and the current item-service has no Marketing permission interceptor. Without a verified fail-closed authorization path, do not implement or expose the admin endpoints and do not switch routes.

- [ ] If identity ambiguity, missing/ambiguous product mapping, ledger projection anomaly, stock-balance mismatch, or unresolved service-side authorization remains, record the sanitized finding and mark the next data migration/cutover step blocked pending an explicit decision. Do not continue into schema backfill or controller routing.

- [ ] Only after those facts are verified and the decisions are recorded should a separate, code-level migration plan be written for item-service schema/entities, `hm-api` contracts, consumer migration, API compatibility, and rollback. This plan does not authorize those changes.

## Verification

Run from the repository root:

```powershell
python -B -m unittest tests.test_marketing_identity_rbac_preflight
python -B -m unittest tests.test_marketing_catalog_inventory_preflight
git diff --check
```

Expected: both tests pass; `git diff --check` reports no whitespace errors. A test pass does not mean a database audit ran. The SQL execution status must be reported separately as `已运行` or `未运行` with the actual reason.
