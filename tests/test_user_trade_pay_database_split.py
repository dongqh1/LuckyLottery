from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class DatabaseSplitContractTest(unittest.TestCase):
    DATABASES = {
        "user": ("hm_user", "hm_user_app", "change-user-db-password", ("user", "address")),
        "trade": ("hm_trade", "hm_trade_app", "change-trade-db-password", ("order", "order_detail", "order_logistics")),
        "pay": ("hm_pay", "hm_pay_app", "change-pay-db-password", ("pay_order",)),
    }

    def read(self, relative_path: str) -> str:
        return (ROOT / relative_path).read_text(encoding="utf-8")

    def test_migrations_copy_current_hmall_tables_without_deleting_sources(self):
        files = {
            "user": "mysql/migrations/V006__create_user_service_database.sql",
            "trade": "mysql/migrations/V007__create_trade_service_database.sql",
            "pay": "mysql/migrations/V008__create_pay_service_database.sql",
        }
        for domain, relative_path in files.items():
            database, account, _, tables = self.DATABASES[domain]
            sql = self.read(relative_path)
            self.assertIn(f"CREATE DATABASE IF NOT EXISTS `{database}`", sql)
            self.assertIn(f"'{account}'@'%'", sql)
            self.assertNotIn("DROP TABLE", sql.upper())
            self.assertNotIn("DELETE FROM `HMALL`", sql.upper())
            for table in tables:
                self.assertIn(
                    f"CREATE TABLE IF NOT EXISTS `{database}`.`{table}` LIKE `hmall`.`{table}`",
                    sql,
                )
                self.assertIn(
                    f"INSERT IGNORE INTO `{database}`.`{table}` SELECT * FROM `hmall`.`{table}`",
                    sql,
                )

    def test_compose_runs_all_three_migrations_with_yaml_credentials(self):
        compose = self.read("compose.yaml")
        for domain, (database, _, _, _) in self.DATABASES.items():
            migration_number = {"user": "006", "trade": "007", "pay": "008"}[domain]
            variable = f"HM_{domain.upper()}_DB_PASSWORD"
            self.assertIn(
                f"{variable}: ${{{variable}:?set {variable} in .env}}", compose
            )
            self.assertIn(f"V{migration_number}__create_{domain}_service_database.sql", compose)
            self.assertIn(f"@{database}_password", compose)

    def test_service_profiles_use_isolated_databases_and_environment_passwords(self):
        for domain, (database, account, default_password, _) in self.DATABASES.items():
            prefix = f"HM_{domain.upper()}_DB"
            local = self.read(f"{domain}-service/src/main/resources/application-local.yaml")
            dev = self.read(f"{domain}-service/src/main/resources/application-dev.yaml")
            for content in (local, dev):
                self.assertIn(f"database: {database}", content)
                self.assertIn(f"username: {account}", content)
                self.assertIn(
                    f"password: ${{{prefix}_PASSWORD:{default_password}}}", content
                )
            self.assertIn("host: 127.0.0.1", local)
            self.assertIn(f"port: ${{{prefix}_PORT:13307}}", local)
            self.assertIn(f"host: ${{{prefix}_HOST:mysql}}", dev)
            self.assertIn(f"port: ${{{prefix}_PORT:3306}}", dev)
            self.assertIn(f"{prefix}_PASSWORD", self.read(".env.example"))

    def test_every_local_profile_loads_the_root_dotenv(self):
        for domain in ("user", "trade", "pay"):
            local = self.read(f"{domain}-service/src/main/resources/application-local.yaml")
            self.assertIn("optional:file:.env[.properties]", local)
            self.assertIn("optional:file:../.env[.properties]", local)

    def test_trade_and_pay_base_yaml_define_datasource(self):
        for domain in ("trade", "pay"):
            application = self.read(f"{domain}-service/src/main/resources/application.yaml")
            self.assertIn("active: ${SPRING_PROFILES_ACTIVE:local}", application)
            self.assertIn("jdbc:mysql://${hm.db.host}:${hm.db.port}/${hm.db.database}", application)
            self.assertIn("username: ${hm.db.username}", application)
            self.assertIn("password: ${hm.db.password}", application)


if __name__ == "__main__":
    unittest.main()
