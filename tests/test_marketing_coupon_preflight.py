import re
import unittest
from pathlib import Path


SQL_PATH = (
    Path(__file__).resolve().parents[1]
    / "scripts"
    / "migration"
    / "marketing-coupon-preflight.sql"
)


class MarketingCouponPreflightTests(unittest.TestCase):
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

    def test_script_never_selects_all_columns_or_returns_sensitive_values(self):
        self.assertNotRegex(self.sql_without_comments, r"(?i)\bSELECT\s+\*")
        self.assertNotRegex(
            self.sql_without_comments,
            r"(?i)\b(username|phone|password|password_hash|coupon_no|template_code|template_name|coupon_name|product_name|order_no|payment_no|locked_order_no|used_order_no)\b",
        )

    def test_script_uses_explicit_schemas_and_reports_coupon_aggregates(self):
        for token in (
            "luckyhub.coupon_template",
            "luckyhub.user_coupon",
            "luckyhub.coupon_issue_record",
            "luckyhub.mall_order",
            "hmall.item",
            "information_schema.tables",
            "coupon_template_total",
            "coupon_template_status",
            "coupon_template_scope_distribution",
            "user_coupon_status",
            "user_coupon_total",
            "coupon_issue_record_total",
            "COUNT(*)",
        ):
            with self.subTest(token=token):
                self.assertIn(token, self.sql_without_comments)

    def test_script_aggregates_coupon_reference_anomalies(self):
        for token in (
            "user_coupon_missing_template",
            "coupon_issue_missing_template",
            "coupon_issue_missing_user_coupon",
            "coupon_issue_mapping_mismatch",
            "duplicate_coupon_issue_business_key_groups",
            "mall_order_missing_user_coupon",
            "mall_order_missing_coupon_template",
            "mall_order_coupon_mapping_mismatch",
            "coupon_template_missing_hmall_item",
            "coupon_template_legacy_product_missing",
        ):
            with self.subTest(token=token):
                self.assertIn(token, self.sql_without_comments)

    def test_script_reports_mysql_server_version(self):
        self.assertIn(
            "SELECT VERSION() AS mysql_server_version",
            self.sql_without_comments,
        )


if __name__ == "__main__":
    unittest.main()
