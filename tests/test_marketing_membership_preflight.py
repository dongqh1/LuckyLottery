import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SQL_PATH = ROOT / "scripts" / "migration" / "marketing-membership-preflight.sql"
REPORT_PATH = (
    ROOT
    / "docs"
    / "superpowers"
    / "audits"
    / "2026-09-25-marketing-membership-preflight-results.md"
)


class MarketingMembershipPreflightTests(unittest.TestCase):
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

    def test_schema_inventory_checks_both_marketing_and_hmall_user_tables(self):
        sql = SQL_PATH.read_text(encoding="utf-8")
        self.assertIn("'luckyhub'", sql)
        self.assertIn("'hm_user'", sql)
        self.assertIn("'user'", sql)
        self.assertIn("information_schema.tables", sql.lower())

    def test_business_table_references_are_schema_qualified(self):
        sql = SQL_PATH.read_text(encoding="utf-8")
        for table in (
            "membership_product",
            "user_membership",
            "membership_grant_record",
            "sys_user",
            "reward_definition",
            "lottery_draw_record",
            "user_benefit",
        ):
            with self.subTest(table=table):
                self.assertRegex(
                    sql,
                    rf"(?i)\b(?:FROM|JOIN)\s+luckyhub\.{table}\b",
                )
        self.assertRegex(sql, r"(?i)\bJOIN\s+information_schema\.tables\b")

    def test_queries_report_only_aggregate_membership_and_reward_metrics(self):
        sql = SQL_PATH.read_text(encoding="utf-8")
        required_aliases = (
            "audit_execution_utc",
            "mysql_server_version",
            "membership_product_total",
            "enabled_product_total",
            "disabled_product_total",
            "user_membership_total",
            "active_membership_total",
            "expired_membership_total",
            "membership_grant_record_total",
            "duplicate_business_key_groups",
            "membership_missing_marketing_user_total",
            "grant_missing_marketing_user_total",
            "grant_missing_membership_product_total",
            "reward_definition_orphan_product_reference_total",
            "lottery_snapshot_target_payload_mismatch_total",
            "user_benefit_snapshot_target_payload_mismatch_total",
        )
        for alias in required_aliases:
            with self.subTest(alias=alias):
                self.assertIn(alias, sql)

    def test_sql_does_not_project_row_level_identity_or_product_values(self):
        sql = SQL_PATH.read_text(encoding="utf-8")
        self.assertNotRegex(sql, r"(?i)\bSELECT\s+\*")
        projections = re.findall(r"(?is)\bSELECT\s+(.*?)\s+FROM\b", sql)
        forbidden_projection = re.compile(
            r"(?i)\b(username|phone|mobile|password|password_hash|business_no|user_id|product_code|product_name|order_no)\b"
        )
        for projection in projections:
            with self.subTest(projection=projection[:80]):
                self.assertIsNone(forbidden_projection.search(projection))
        self.assertNotRegex(
            sql,
            r"(?i)\b(INTO\s+(OUTFILE|DUMPFILE)|FOR\s+UPDATE|LOAD_FILE|SLEEP\s*\()",
        )

    def test_results_template_keeps_database_evidence_unrun(self):
        self.assertTrue(REPORT_PATH.is_file(), f"Missing report template: {REPORT_PATH}")
        report = REPORT_PATH.read_text(encoding="utf-8")
        self.assertIn("审计状态：未运行", report)
        self.assertIn("未采集", report)
        self.assertIn("SELECT", report)


if __name__ == "__main__":
    unittest.main()
