import re
import unittest
from pathlib import Path


class MarketingIdentityRbacPreflightTests(unittest.TestCase):
    script_path = (
        Path(__file__).resolve().parents[1]
        / "scripts/migration/marketing-identity-rbac-preflight.sql"
    )

    def test_script_exists_and_checks_both_identity_databases(self):
        sql = self.script_path.read_text(encoding="utf-8")
        self.assertIn("luckyhub.sys_user", sql)
        self.assertIn("hm_user.user", sql)
        self.assertIn("information_schema.columns", sql)

    def test_script_contains_only_read_queries(self):
        sql = re.sub(
            r"(?m)^\s*--.*$", "", self.script_path.read_text(encoding="utf-8")
        )
        sql = re.sub(r"/\*.*?\*/", "", sql, flags=re.S)
        self.assertNotRegex(
            sql,
            r"(?i)\b(INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP|TRUNCATE|RENAME|CALL)\b",
        )

    def test_script_reports_mysql_server_version(self):
        sql = self.script_path.read_text(encoding="utf-8").lower()
        self.assertIn("select version() as mysql_server_version", sql)

    def test_script_reports_identity_and_rbac_conflicts_without_selecting_passwords(
        self,
    ):
        sql = self.script_path.read_text(encoding="utf-8").lower()
        for required in (
            "username",
            "sys_user_role",
            "sys_role_permission",
            "status",
            "password regexp",
        ):
            self.assertIn(required, sql)
        self.assertNotRegex(sql, r"(?is)select\s+[^;]*\bpassword\s*(,|from)")


if __name__ == "__main__":
    unittest.main()
