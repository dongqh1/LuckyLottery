import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SQL_PATH = ROOT / "scripts" / "migration" / "marketing-points-preflight.sql"
REPORT_PATH = (
    ROOT
    / "docs"
    / "superpowers"
    / "audits"
    / "2026-09-25-marketing-points-preflight-results.md"
)


class MarketingPointsPreflightTests(unittest.TestCase):
    def test_script_exists_and_contains_only_read_only_selects(self):
        self.assertTrue(SQL_PATH.is_file(), f"Missing SQL script: {SQL_PATH}")
        sql = SQL_PATH.read_text(encoding="utf-8")
        uncommented = re.sub(r"/\*.*?\*/|--[^\r\n]*|\#[^\r\n]*", "", sql, flags=re.DOTALL)
        statements = [part.strip() for part in uncommented.split(";") if part.strip()]
        self.assertTrue(statements)
        for statement in statements:
            self.assertRegex(statement, r"(?is)^SELECT\b")
            self.assertNotRegex(
                statement,
                r"(?i)\b(ALTER|CREATE|DELETE|DROP|GRANT|INSERT|RENAME|REPLACE|REVOKE|TRUNCATE|UPDATE|USE|CALL|EXECUTE|PREPARE)\b",
            )

    def test_inventory_covers_points_user_catalog_redemption_and_fulfillment_tables(self):
        sql = SQL_PATH.read_text(encoding="utf-8")
        self.assertIn("information_schema.tables", sql.lower())
        for schema in ("luckyhub", "hm_item"):
            with self.subTest(schema=schema):
                self.assertIn(f"'{schema}'", sql)
        required_tables = (
            "points_account",
            "points_ledger",
            "points_redemption_order",
            "points_redemption_offer",
            "sys_user",
            "product",
            "product_sku",
            "shipping_address_snapshot",
            "shipping_order",
            "item",
        )
        for table in required_tables:
            with self.subTest(table=table):
                self.assertRegex(sql, rf"(?i)'{table}'|\b(?:luckyhub|hm_item)\.{table}\b")

    def test_business_table_reads_are_schema_qualified(self):
        sql = SQL_PATH.read_text(encoding="utf-8")
        for table in (
            "points_account",
            "points_ledger",
            "points_redemption_order",
            "points_redemption_offer",
            "sys_user",
            "product",
            "product_sku",
            "shipping_address_snapshot",
            "shipping_order",
        ):
            with self.subTest(table=table):
                self.assertRegex(sql, rf"(?i)\b(?:FROM|JOIN)\s+luckyhub\.{table}\b")
        self.assertRegex(sql, r"(?i)\b(?:FROM|JOIN)\s+hm_item\.item\b")
        self.assertRegex(sql, r"(?i)\bJOIN\s+information_schema\.tables\b")

    def test_queries_report_aggregate_metrics_without_export_or_row_dump(self):
        sql = SQL_PATH.read_text(encoding="utf-8")
        required_aliases = (
            "audit_execution_utc",
            "mysql_server_version",
            "points_account_total",
            "account_missing_marketing_user_total",
            "accounts_without_ledger_total",
            "account_latest_ledger_balance_mismatch_total",
            "points_ledger_total",
            "ledger_missing_marketing_user_total",
            "reversal_missing_original_total",
            "points_redemption_order_total",
            "redemption_missing_marketing_user_total",
            "legacy_sku_orders_missing_sku_total",
            "hmall_offer_orders_offer_item_mismatch_total",
            "completed_physical_redemptions_missing_shipping_total",
            "completed_redemptions_missing_debit_ledger_total",
            "reversed_redemptions_missing_reversal_ledger_total",
        )
        for alias in required_aliases:
            with self.subTest(alias=alias):
                self.assertIn(alias, sql)
        self.assertNotRegex(sql, r"(?i)\bSELECT\s+\*")
        self.assertNotRegex(
            sql,
            r"(?i)\b(INTO\s+(OUTFILE|DUMPFILE)|FOR\s+UPDATE|LOAD_FILE|SLEEP\s*\()",
        )
        output_aliases = re.findall(r"(?i)\bAS\s+([A-Za-z_][A-Za-z0-9_]*)", sql)
        forbidden_alias = re.compile(
            r"(?i)^(username|phone|mobile|password|password_hash|user_id|business_id|business_no|redemption_no|remark|failure_reason|product_name|item_name|sku_name)$"
        )
        for alias in output_aliases:
            with self.subTest(alias=alias):
                self.assertIsNone(forbidden_alias.match(alias))

    def test_results_template_keeps_database_evidence_unrun(self):
        self.assertTrue(REPORT_PATH.is_file(), f"Missing report template: {REPORT_PATH}")
        report = REPORT_PATH.read_text(encoding="utf-8")
        self.assertIn("审计状态：未运行", report)
        self.assertIn("未采集", report)
        self.assertIn("SELECT", report)


if __name__ == "__main__":
    unittest.main()
