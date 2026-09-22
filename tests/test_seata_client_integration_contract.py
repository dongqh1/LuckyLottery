import json
from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
SERVICES = (
    "item-service",
    "cart-service",
    "user-service",
    "trade-service",
    "pay-service",
    "marketing-service",
)


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def compose_config() -> dict:
    result = subprocess.run(
        [
            "docker", "compose", "--env-file", str(ROOT / ".env.example"),
            "-f", str(ROOT / "compose.yaml"), "config", "--format", "json",
        ],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        raise AssertionError(
            f"docker compose config failed:\n{result.stdout}\n{result.stderr}"
        )
    return json.loads(result.stdout)


class SeataClientDependencyContractTest(unittest.TestCase):
    def test_root_pins_seata_2_5_0(self):
        pom = read("pom.xml")
        self.assertIn("<seata.version>2.5.0</seata.version>", pom)
        self.assertRegex(
            pom,
            r"<artifactId>seata-spring-boot-starter</artifactId>\s*"
            r"<version>\$\{seata\.version}</version>",
        )

    def test_all_database_services_use_spring_cloud_alibaba_seata(self):
        for service in SERVICES:
            with self.subTest(service=service):
                pom = read(f"{service}/pom.xml")
                self.assertIn(
                    "<artifactId>spring-cloud-starter-alibaba-seata</artifactId>",
                    pom,
                )

    def test_marketing_supplies_nacos_client_for_seata_registry(self):
        pom = read("marketing-service/pom.xml")
        self.assertIn("<artifactId>nacos-client</artifactId>", pom)


class SeataClientConfigurationContractTest(unittest.TestCase):
    def test_all_database_services_share_at_transaction_group(self):
        required = (
            "tx-service-group: hmall_tx_group",
            "enable-auto-data-source-proxy: true",
            "data-source-proxy-mode: AT",
            "hmall_tx_group: default",
            "type: nacos",
            "application: seata-server",
            "cluster: default",
            "${NACOS_SERVER_ADDR:127.0.0.1:18848}",
            "${NACOS_USERNAME:nacos}",
            "${NACOS_PASSWORD:nacos}",
        )
        for service in SERVICES:
            with self.subTest(service=service):
                application = read(f"{service}/src/main/resources/application.yaml")
                self.assertIn("seata:", application)
                for fragment in required:
                    self.assertIn(fragment, application)


class SeataUndoLogContractTest(unittest.TestCase):
    def test_each_hmall_database_provisions_undo_log(self):
        migrations = {
            "hm_item": "mysql/migrations/V004__create_item_service_database.sql",
            "hm_cart": "mysql/migrations/V005__create_cart_service_database.sql",
            "hm_user": "mysql/migrations/V006__create_user_service_database.sql",
            "hm_trade": "mysql/migrations/V007__create_trade_service_database.sql",
            "hm_pay": "mysql/migrations/V008__create_pay_service_database.sql",
        }
        for schema, path in migrations.items():
            with self.subTest(schema=schema):
                sql = re.sub(r"\s+", " ", read(path)).lower()
                self.assertRegex(
                    sql,
                    rf"create table if not exists `{schema}`\.`undo_log`",
                )
                self.assertIn("unique key `ux_undo_log` (`xid`, `branch_id`)", sql)
                self.assertIn("key `ix_log_created` (`log_created`)", sql)

    def test_luckyhub_flyway_provisions_undo_log(self):
        sql = re.sub(
            r"\s+",
            " ",
            read("marketing-service/src/main/resources/db/migration/"
                 "V27__add_seata_undo_log.sql"),
        ).lower()
        self.assertRegex(sql, r"create table if not exists `undo_log`")
        self.assertIn("unique key `ux_undo_log` (`xid`, `branch_id`)", sql)
        self.assertIn("key `ix_log_created` (`log_created`)", sql)


class SeataComposeClientContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.services = compose_config()["services"]

    def test_server_and_clients_use_2_5_runtime(self):
        seata = self.services["seata-server"]
        self.assertIn("2.5.0", seata["image"])
        self.assertIn("8091", " ".join(seata["healthcheck"]["test"]))
        self.assertEqual("8091", str(seata["environment"]["SEATA_PORT"]))

    def test_all_clients_wait_for_healthy_tc_and_receive_registry_settings(self):
        for service in SERVICES:
            with self.subTest(service=service):
                definition = self.services[service]
                self.assertEqual(
                    "service_healthy",
                    definition["depends_on"]["seata-server"]["condition"],
                )
                environment = definition["environment"]
                self.assertEqual("true", str(environment["SEATA_ENABLED"]).lower())
                self.assertEqual("nacos:8848", environment["NACOS_SERVER_ADDR"])
                self.assertIn("NACOS_USERNAME", environment)
                self.assertIn("NACOS_PASSWORD", environment)


class SeataTransactionBoundaryContractTest(unittest.TestCase):
    def assert_global_transaction(self, source: str, method: str):
        pattern = (
            r"@GlobalTransactional\s*\([^)]*\)\s*"
            r"(?:@Override\s*)?(?:@Transactional\s*)?"
            r"public\s+[^\s]+\s+" + re.escape(method) + r"\s*\("
        )
        self.assertRegex(source, pattern)

    def test_trade_write_orchestrations_start_global_transactions(self):
        source = read(
            "trade-service/src/main/java/com/dongqh/tradeservice/"
            "service/impl/OrderServiceImpl.java"
        )
        for method in ("createOrder", "applyCheckoutCoupon", "cancelOrder"):
            with self.subTest(method=method):
                self.assert_global_transaction(source, method)

    def test_balance_payment_starts_global_transaction(self):
        source = read(
            "pay-service/src/main/java/com/dongqh/payservice/"
            "service/impl/PayOrderServiceImpl.java"
        )
        self.assert_global_transaction(source, "tryPayOrderByBalance")


class SeataHttpPropagationContractTest(unittest.TestCase):
    def test_common_and_marketing_register_http_client_customizers(self):
        common = read(
            "mall-backend/hm-common/src/main/java/com/hmall/common/config/"
            "SeataHttpClientAutoConfiguration.java"
        )
        marketing = read(
            "marketing-service/src/main/java/com/dongqh/luckyhub/config/"
            "SeataHttpClientConfiguration.java"
        )
        for source in (common, marketing):
            self.assertIn("RootContext.getXID()", source)
            self.assertIn("RootContext.KEY_XID", source)
            self.assertIn("RestTemplateCustomizer", source)
            self.assertIn("RestClientCustomizer", source)


if __name__ == "__main__":
    unittest.main()
