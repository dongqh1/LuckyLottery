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
