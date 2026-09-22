import json
from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
SEATA_SQL = ROOT / "mysql" / "seata" / "seata-tc.sql"


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


class SeataDatabaseContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = SEATA_SQL.read_text(encoding="utf-8")
        cls.normalized = re.sub(r"\s+", " ", cls.sql).lower()

    def test_database_and_dedicated_user_are_provisioned_repeatably(self):
        self.assertRegex(
            self.normalized,
            r"create database if not exists\s+`?seata`?.*character set\s+utf8mb4",
        )
        self.assertIn("create user if not exists 'seata_app'@'%'", self.normalized)
        self.assertIn("alter user ''seata_app''@''%'' identified by", self.normalized)
        self.assertRegex(
            self.normalized,
            r"grant\s+select\s*,\s*insert\s*,\s*update\s*,\s*delete\s+on\s+`?seata`?\.\*\s+to\s+'seata_app'@'%'",
        )
        self.assertNotRegex(
            self.normalized,
            r"grant\s+all privileges.*'seata_app'",
        )

    def test_all_tc_tables_and_distributed_locks_are_idempotent(self):
        for table in (
            "global_table",
            "branch_table",
            "lock_table",
            "distributed_lock",
            "vgroup_table",
        ):
            with self.subTest(table=table):
                self.assertRegex(
                    self.normalized,
                    rf"create table if not exists\s+`?{table}`?",
                )
        self.assertEqual(4, len(re.findall(r"insert ignore into\s+`?distributed_lock`?", self.normalized)))

    def test_schema_init_mounts_and_executes_seata_sql_with_injected_password(self):
        service = compose_config()["services"]["schema-init"]
        mounts = {
            mount["target"]: mount for mount in service["volumes"]
        }
        self.assertIn("/migrations/seata-tc.sql", mounts)
        self.assertTrue(mounts["/migrations/seata-tc.sql"]["read_only"])
        self.assertIn("SEATA_DB_PASSWORD", service["environment"])
        command = " ".join(
            service["command"]
            if isinstance(service["command"], list)
            else [service["command"]]
        )
        self.assertIn("SET @seata_db_password", command)
        self.assertIn("/migrations/seata-tc.sql", command)


class SeataRuntimeConfigurationContractTest(unittest.TestCase):
    def test_application_uses_container_dns_and_environment_secrets(self):
        application = read("seata/application.yml")
        self.assertIn("web-application-type: none", application)
        self.assertIn("server-addr: nacos:8848", application)
        self.assertIn("url: jdbc:mysql://mysql:3306/seata", application)
        self.assertIn("allowPublicKeyRetrieval=true", application)
        self.assertIn("useSSL=false", application)
        self.assertIn("user: seata_app", application)
        self.assertIn("vgroup-table: vgroup_table", application)
        for variable in (
            "NACOS_USERNAME",
            "NACOS_PASSWORD",
            "SEATA_DB_PASSWORD",
            "SEATA_SECURITY_SECRET_KEY",
        ):
            with self.subTest(variable=variable):
                self.assertIn(f"${{{variable}}}", application)
        self.assertNotIn("password: 123", application)


class SeataImageContractTest(unittest.TestCase):
    def test_derived_image_reproducibly_supplies_mysql_connector(self):
        dockerfile = read("seata/Dockerfile")
        self.assertRegex(
            dockerfile,
            r"(?m)^FROM\s+apache/seata-server:2\.5\.0\.jdk17\s*$",
        )
        self.assertRegex(
            dockerfile,
            r"(?m)^ARG\s+MYSQL_CONNECTOR_VERSION=9\.7\.0\s*$",
        )
        self.assertRegex(
            dockerfile,
            r"(?m)^ARG\s+MYSQL_CONNECTOR_SHA256="
            r"0353648eaa1c91e0f4020c959abf756bc866ffd583df22ae6b6f6e0cbd43eb44\s*$",
        )
        self.assertIn(
            "https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/"
            "${MYSQL_CONNECTOR_VERSION}/mysql-connector-j-"
            "${MYSQL_CONNECTOR_VERSION}.jar",
            dockerfile,
        )
        self.assertIn(
            "/seata-server/libs/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar",
            dockerfile,
        )
        self.assertIn("sha256sum --check", dockerfile)
        self.assertNotIn("LOADER_PATH", dockerfile)


class SeataComposeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = compose_config()
        cls.services = cls.config["services"]

    def test_tc_uses_pinned_image_resources_and_dependencies(self):
        service = self.services["seata-server"]
        self.assertEqual(
            "hmall-lottery-seata-server:2.5.0-jdk17-mysql9.7.0",
            service["image"],
        )
        build = service["build"]
        self.assertEqual(ROOT.resolve(), Path(build["context"]).resolve())
        self.assertEqual(
            "seata/Dockerfile",
            Path(build["dockerfile"]).as_posix(),
        )
        self.assertEqual(
            "service_completed_successfully",
            service["depends_on"]["schema-init"]["condition"],
        )
        self.assertEqual(
            "service_healthy",
            service["depends_on"]["nacos"]["condition"],
        )
        mounts = {mount["target"]: mount for mount in service["volumes"]}
        self.assertIn("/seata-server/resources/application.yml", mounts)
        self.assertTrue(
            mounts["/seata-server/resources/application.yml"]["read_only"]
        )
        self.assertNotIn("/seata-server/resources", mounts)
        self.assertIn("healthcheck", service)

    def test_tc_and_dependencies_share_only_the_existing_default_network(self):
        for name in ("mysql", "nacos", "seata-server"):
            with self.subTest(service=name):
                self.assertEqual({"default"}, set(self.services[name]["networks"]))
        self.assertEqual(
            "hmall-lottery_default",
            self.config["networks"]["default"]["name"],
        )
        self.assertNotIn("hm-net", json.dumps(self.config))

    def test_tc_exposes_rpc_and_receives_only_named_secrets(self):
        service = self.services["seata-server"]
        published = {
            (int(port["target"]), int(port["published"]))
            for port in service["ports"]
        }
        self.assertIn((8091, 18091), published)
        self.assertNotIn((8091, 17099), published)
        for variable in (
            "SEATA_DB_PASSWORD",
            "NACOS_USERNAME",
            "NACOS_PASSWORD",
            "SEATA_SECURITY_SECRET_KEY",
        ):
            self.assertIn(variable, service["environment"])


class SeataDocumentationContractTest(unittest.TestCase):
    def test_readme_documents_tc_start_and_scope(self):
        readme = read("README.md")
        self.assertIn("docker compose --env-file .env up -d --build seata-server", readme)
        self.assertIn("localhost:18091", readme)
        self.assertIn("不提供内置 Web 控制台", readme)
        self.assertIn("global_table", readme)
        self.assertIn("vgroup_table", readme)
        self.assertIn("undo_log", readme)
        self.assertIn("hmall-lottery_default", readme)


if __name__ == "__main__":
    unittest.main()
