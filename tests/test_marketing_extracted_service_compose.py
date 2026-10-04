import pathlib
import unittest

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
NEW_SERVICES = {
    "coupon-service": ("COUPON", "hm_coupon", "hm_coupon_app"),
    "points-service": ("POINTS", "hm_points", "hm_points_app"),
    "membership-service": ("MEMBERSHIP", "hm_membership", "hm_membership_app"),
    "lottery-service": ("LOTTERY", "hm_lottery", "hm_lottery_app"),
    "fulfillment-service": ("FULFILLMENT", "hm_fulfillment", "hm_fulfillment_app"),
}


class MarketingExtractedServiceComposeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compose_text = (ROOT / "compose.yaml").read_text(encoding="utf-8")
        cls.compose = yaml.safe_load(cls.compose_text)
        cls.services = cls.compose["services"]
        cls.env_example = (ROOT / ".env.example").read_text(encoding="utf-8")

    def test_new_services_are_opt_in_and_have_isolated_databases(self):
        for service_name, (prefix, schema, username) in NEW_SERVICES.items():
            with self.subTest(service=service_name):
                service = self.services[service_name]
                environment = service["environment"]
                self.assertEqual(["marketing-extraction"], service["profiles"])
                self.assertEqual(f"{service_name}/Dockerfile", service["build"]["dockerfile"])
                self.assertEqual(
                    f"jdbc:mysql://mysql:3306/{schema}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                    environment[f"{prefix}_DATABASE_URL"],
                )
                self.assertEqual(username, environment[f"{prefix}_DATABASE_USERNAME"])
                self.assertEqual("true", environment[f"{prefix}_FLYWAY_ENABLED"])
                self.assertIn("marketing-extraction-schema-init", service["depends_on"])
                for existing_service in ("schema-init", "item-service", "user-service"):
                    self.assertNotIn(existing_service, service["depends_on"])
                self.assertNotIn("marketing-service", service["depends_on"])
                self.assertEqual(
                    ["CMD-SHELL", "bash -ec 'exec 3<>/dev/tcp/127.0.0.1/8080'"],
                    service["healthcheck"]["test"],
                )

    def test_side_by_side_writers_and_lottery_state_are_isolated(self):
        coupon_environment = self.services["coupon-service"]["environment"]
        fulfillment_environment = self.services["fulfillment-service"]["environment"]
        lottery_environment = self.services["lottery-service"]["environment"]

        self.assertEqual("false", coupon_environment["COUPON_MESSAGING_ENABLED"])
        self.assertEqual("false", fulfillment_environment["FULFILLMENT_MESSAGING_ENABLED"])
        self.assertEqual("false", lottery_environment["LOTTERY_OUTBOX_ENABLED"])
        self.assertEqual("1", lottery_environment["LOTTERY_REDIS_DATABASE"])

    def test_gateway_does_not_require_legacy_marketing_or_extracted_services(self):
        gateway_dependencies = self.services["hm-gateway"]["depends_on"]
        nginx_dependencies = self.services["nginx"]["depends_on"]

        self.assertNotIn("marketing-service", gateway_dependencies)
        self.assertNotIn("marketing-service", nginx_dependencies)
        for service_name in NEW_SERVICES:
            self.assertNotIn(service_name, gateway_dependencies)
            self.assertNotIn(service_name, nginx_dependencies)

    def test_legacy_marketing_is_opt_in_so_the_extracted_stack_stays_lightweight(self):
        self.assertEqual(["legacy-marketing"], self.services["marketing-service"]["profiles"])

    def test_database_provisioning_uses_each_service_schema_and_account(self):
        migration_path = ROOT / "mysql/migrations/V011__create_marketing_domain_service_databases.sql"
        self.assertTrue(migration_path.is_file())
        migration = migration_path.read_text(encoding="utf-8")
        schema_init = self.services["schema-init"]
        extraction_schema_init = self.services["marketing-extraction-schema-init"]

        migration_mount = "./mysql/migrations/V011__create_marketing_domain_service_databases.sql:/migrations/V011__create_marketing_domain_service_databases.sql:ro"
        self.assertNotIn(migration_mount, schema_init["volumes"])
        self.assertNotIn("V011__create_marketing_domain_service_databases.sql", schema_init["command"][0])
        self.assertEqual(["marketing-extraction"], extraction_schema_init["profiles"])
        self.assertIn("mysql", extraction_schema_init["depends_on"])

        for prefix, schema, username in NEW_SERVICES.values():
            with self.subTest(schema=schema):
                self.assertIn(f"CREATE DATABASE IF NOT EXISTS `{schema}`", migration)
                self.assertIn(f"CREATE USER IF NOT EXISTS '{username}'@'%'", migration)
                self.assertIn(f"ON `{schema}`.* TO '{username}'@'%'", migration)
                self.assertIn(f"{prefix}_DB_PASSWORD", extraction_schema_init["environment"])
                self.assertNotIn(f"{prefix}_DB_PASSWORD", schema_init["environment"])
        self.assertIn(
            migration_mount,
            extraction_schema_init["volumes"],
        )
        self.assertNotIn("ALTER USER", migration)

    def test_new_service_database_passwords_fail_closed(self):
        extraction_schema_init = self.services["marketing-extraction-schema-init"]
        command = extraction_schema_init["command"][0]

        for service_name, (prefix, _, _) in NEW_SERVICES.items():
            with self.subTest(service=service_name):
                password_variable = f"{prefix}_DB_PASSWORD"
                expected = "${" + password_variable + ":-}"
                self.assertEqual(expected, extraction_schema_init["environment"][password_variable])
                self.assertEqual(
                    expected,
                    self.services[service_name]["environment"][f"{prefix}_DATABASE_PASSWORD"],
                )
                self.assertIn(f'test -n "$${password_variable}"', command)

    def test_env_example_does_not_supply_extraction_database_passwords(self):
        for prefix, _, _ in NEW_SERVICES.values():
            with self.subTest(prefix=prefix):
                self.assertIn(f"{prefix}_DB_PASSWORD=\n", self.env_example)

    def test_item_clients_have_explicit_compose_urls(self):
        for service_name in ("coupon-service", "points-service"):
            with self.subTest(service=service_name):
                self.assertEqual(
                    "http://item-service:8081",
                    self.services[service_name]["environment"]["ITEM_SERVICE_BASE_URL"],
                )


if __name__ == "__main__":
    unittest.main()
