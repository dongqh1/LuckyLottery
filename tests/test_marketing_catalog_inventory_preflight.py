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
