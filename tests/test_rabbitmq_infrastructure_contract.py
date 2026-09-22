import json
from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def compose_config() -> dict:
    result = subprocess.run(
        [
            "docker",
            "compose",
            "--env-file",
            str(ROOT / ".env.example"),
            "-f",
            str(ROOT / "compose.yaml"),
            "config",
            "--format",
            "json",
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


class RabbitMqComposeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = compose_config()
        cls.services = cls.config["services"]

    def test_broker_uses_pinned_image_network_and_healthcheck(self):
        service = self.services["rabbitmq"]
        self.assertEqual("rabbitmq:4.3.5-management-alpine", service["image"])
        self.assertEqual("rabbitmq", service["hostname"])
        self.assertEqual("unless-stopped", service["restart"])
        self.assertEqual({"default"}, set(service["networks"]))
        self.assertEqual(
            "hmall-lottery_default",
            self.config["networks"]["default"]["name"],
        )
        healthcheck = " ".join(service["healthcheck"]["test"])
        self.assertIn("rabbitmq-diagnostics", healthcheck)
        self.assertIn("ping", healthcheck)

    def test_broker_exposes_ports_credentials_and_persistent_data(self):
        service = self.services["rabbitmq"]
        published = {
            (int(port["target"]), int(port["published"]))
            for port in service["ports"]
        }
        self.assertEqual({(5672, 5672), (15672, 15672)}, published)
        self.assertEqual(
            {
                "RABBITMQ_DEFAULT_USER": "change-rabbitmq-username",
                "RABBITMQ_DEFAULT_PASS": "change-rabbitmq-password",
                "RABBITMQ_DEFAULT_VHOST": "/dongqh",
                "TZ": "Asia/Shanghai",
            },
            service["environment"],
        )
        mounts = {mount["target"]: mount for mount in service["volumes"]}
        self.assertIn("/var/lib/rabbitmq", mounts)
        self.assertEqual(
            "rabbitmq-data",
            mounts["/var/lib/rabbitmq"]["source"],
        )

    def test_amqp_services_receive_connection_values_without_startup_dependency(self):
        for service_name in ("marketing-service", "trade-service", "pay-service"):
            with self.subTest(service=service_name):
                service = self.services[service_name]
                self.assertEqual("rabbitmq", service["environment"]["RABBITMQ_HOST"])
                self.assertEqual("5672", service["environment"]["RABBITMQ_PORT"])
                self.assertEqual(
                    "change-rabbitmq-username",
                    service["environment"]["RABBITMQ_USERNAME"],
                )
                self.assertEqual(
                    "change-rabbitmq-password",
                    service["environment"]["RABBITMQ_PASSWORD"],
                )
                self.assertEqual(
                    "/dongqh",
                    service["environment"]["RABBITMQ_VHOST"],
                )
                self.assertNotIn("rabbitmq", service.get("depends_on", {}))


class RabbitMqProjectConfigurationContractTest(unittest.TestCase):
    def test_example_environment_documents_non_secret_values(self):
        example = read(".env.example")
        for line in (
            "RABBITMQ_USERNAME=change-rabbitmq-username",
            "RABBITMQ_PASSWORD=change-rabbitmq-password",
            "RABBITMQ_VHOST=/dongqh",
            "RABBITMQ_PORT=5672",
            "RABBITMQ_MANAGEMENT_PORT=15672",
        ):
            with self.subTest(line=line):
                self.assertIn(line, example)
        self.assertNotIn("HmallRabbit_2026_Dev", example)

    def test_amqp_services_use_environment_backed_rabbitmq_configuration(self):
        settings = (
            "host: ${RABBITMQ_HOST:localhost}",
            "port: ${RABBITMQ_PORT:5672}",
            "username: ${RABBITMQ_USERNAME:}",
            "password: ${RABBITMQ_PASSWORD:}",
            "virtual-host: ${RABBITMQ_VHOST:/dongqh}",
        )
        application_paths = (
            "marketing-service/src/main/resources/application.yaml",
            "trade-service/src/main/resources/application.yaml",
            "pay-service/src/main/resources/application.yaml",
        )
        for application_path in application_paths:
            application = read(application_path)
            self.assertIn("optional:file:.env[.properties]", application)
            self.assertIn("optional:file:../.env[.properties]", application)
            for setting in settings:
                with self.subTest(application=application_path, setting=setting):
                    self.assertIn(setting, application)

        marketing_sources = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (ROOT / "marketing-service" / "src" / "main").rglob("*")
            if path.is_file() and path.suffix in {".java", ".yaml", ".yml"}
        )
        self.assertNotIn("opsForStream", marketing_sources)
        self.assertNotIn("RedisStream", marketing_sources)


if __name__ == "__main__":
    unittest.main()
