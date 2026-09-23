import hashlib
import json
from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
COMPOSE_FILE = ROOT / "compose.yaml"
MIGRATION_FILE = ROOT / "mysql" / "migrations" / "V001__hmall_marketing_integration.sql"
ITEM_MIGRATION_FILE = ROOT / "mysql" / "migrations" / "V004__create_item_service_database.sql"
CART_MIGRATION_FILE = ROOT / "mysql" / "migrations" / "V005__create_cart_service_database.sql"
NACOS_SCHEMA_FILE = ROOT / "mysql" / "nacos.sql"


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def compose_config(*profiles: str) -> dict:
    command = [
        "docker",
        "compose",
        "--env-file",
        str(ROOT / ".env.example"),
        "-f",
        str(COMPOSE_FILE),
    ]
    for profile in profiles:
        command.extend(["--profile", profile])
    command.extend(["config", "--format", "json"])
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
    if result.returncode != 0:
        raise AssertionError(f"docker compose config failed:\n{result.stdout}\n{result.stderr}")
    return json.loads(result.stdout)


class RequiredFilesContractTest(unittest.TestCase):
    def test_unified_infrastructure_files_exist(self):
        required = [
            "compose.yaml",
            ".env.example",
            "mysql/migrations/V001__hmall_marketing_integration.sql",
            "mysql/migrations/V004__create_item_service_database.sql",
            "mysql/nacos.sql",
            "mysql/nacos/custom.env",
            ".dockerignore",
            "item-service/Dockerfile",
            "cart-service/Dockerfile",
            "user-service/Dockerfile",
            "trade-service/Dockerfile",
            "pay-service/Dockerfile",
            "marketing-service/Dockerfile",
            "hm-gateway/Dockerfile",
            "nginx/Dockerfile",
            "nginx/nginx.conf",
            "scripts/Smoke-Test.ps1",
        ]
        missing = [path for path in required if not (ROOT / path).is_file()]
        self.assertEqual([], missing)

    def test_original_hmall_seed_is_unchanged(self):
        digest = hashlib.sha256((ROOT / "mysql/init/hmall.sql").read_bytes()).hexdigest()
        self.assertEqual("969f73929b4ea80a14deaffe60786da9bfddb10ae82d6d8a1092f0ec16f37a36", digest)


class SmokeScriptContractTest(unittest.TestCase):
    def setUp(self):
        path = ROOT / "scripts" / "Smoke-Test.ps1"
        self.assertTrue(path.is_file(), "scripts/Smoke-Test.ps1 must exist")
        self.script = path.read_text(encoding="utf-8")

    def test_smoke_script_stops_on_http_failures_and_reports_each_boundary(self):
        self.assertIn('$ErrorActionPreference = "Stop"', self.script)
        self.assertIn("Invoke-RestMethod", self.script)
        self.assertIn("MaxAttempts", self.script)
        self.assertIn("RetryDelaySeconds", self.script)
        self.assertIn("Start-Sleep", self.script)
        self.assertIn("Portal static page", self.script)
        self.assertIn("Admin static page", self.script)
        self.assertIn('${admin}/items.html', self.script)
        self.assertIn('Write-Host "PASS', self.script)

    def test_smoke_script_authenticates_and_calls_every_owned_api(self):
        for route in (
            "/api/users/login",
            "/api/search/list",
            "/api/items/",
            "/api/carts",
            "/api/addresses",
            "/api/orders/me",
            "/api/marketing/memberships/me",
        ):
            with self.subTest(route=route):
                self.assertIn(route, self.script)
        self.assertIn("$login.token", self.script)
        self.assertRegex(self.script, r"(?i)authorization")

    def test_smoke_script_contains_no_infrastructure_secrets(self):
        for secret_name in (
            "MYSQL_ROOT_PASSWORD",
            "REDIS_PASSWORD",
            "JWT_SECRET",
            "SHIPPING_ADDRESS_KEY",
        ):
            with self.subTest(secret_name=secret_name):
                self.assertNotIn(secret_name, self.script)


class RuntimeDocumentationContractTest(unittest.TestCase):
    def test_readme_documents_split_start_smoke_test_and_rollback(self):
        readme = read("README.md")
        self.assertIn("docker compose --env-file .env up --build -d", readme)
        self.assertIn(
            r"pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\Smoke-Test.ps1",
            readme,
        )
        self.assertIn(
            "docker compose --env-file .env --profile rollback up --build -d mall-service",
            readme,
        )
        self.assertIn("五个拆分服务", readme)
        self.assertIn("Nginx", readme)


class ComposeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = compose_config()
        cls.services = cls.config["services"]
        cls.rollback_services = compose_config("rollback")["services"]

    def test_compose_defines_the_split_services_as_the_default_stack(self):
        self.assertEqual(
            {
                "mysql",
                "redis",
                "rabbitmq",
                "schema-init",
                "nacos",
                "seata-server",
                "item-service",
                "cart-service",
                "user-service",
                "trade-service",
                "pay-service",
                "hm-gateway",
                "marketing-service",
                "nginx",
            },
            set(self.services),
        )
        self.assertEqual("mysql:8.4", self.services["mysql"]["image"])
        self.assertTrue(self.services["redis"]["image"].startswith("redis:7.4"))

    def test_monolith_is_available_only_in_the_rollback_profile(self):
        self.assertNotIn("mall-service", self.services)
        self.assertIn("mall-service", self.rollback_services)
        self.assertEqual(["rollback"], self.rollback_services["mall-service"]["profiles"])

    def test_mysql_keeps_seed_and_persistent_data(self):
        mounts = self.services["mysql"]["volumes"]
        targets = {mount["target"]: mount for mount in mounts}
        self.assertIn("/var/lib/mysql", targets)
        self.assertEqual("volume", targets["/var/lib/mysql"]["type"])
        self.assertIn("/docker-entrypoint-initdb.d/001_hmall.sql", targets)
        self.assertTrue(targets["/docker-entrypoint-initdb.d/001_hmall.sql"]["read_only"])
        self.assertIn("healthcheck", self.services["mysql"])

    def test_schema_init_runs_incremental_migration_after_mysql_is_healthy(self):
        service = self.services["schema-init"]
        self.assertEqual("mysql:8.4", service["image"])
        self.assertEqual(
            "service_healthy",
            service["depends_on"]["mysql"]["condition"],
        )
        migration_mounts = [
            mount
            for mount in service["volumes"]
            if mount["target"] == "/migrations/V001__hmall_marketing_integration.sql"
        ]
        self.assertEqual(1, len(migration_mounts))
        self.assertTrue(migration_mounts[0]["read_only"])
        command = " ".join(service["command"] if isinstance(service["command"], list) else [service["command"]])
        self.assertIn("V001__hmall_marketing_integration.sql", command)
        self.assertIn("MYSQL_ROOT_PASSWORD", service["environment"])
        self.assertEqual("no", service.get("restart", "no"))

    def test_schema_init_provisions_the_item_database(self):
        service = self.services["schema-init"]
        migration_mounts = [
            mount
            for mount in service["volumes"]
            if mount["target"] == "/migrations/V004__create_item_service_database.sql"
        ]
        self.assertEqual(1, len(migration_mounts))
        self.assertTrue(migration_mounts[0]["read_only"])
        command = " ".join(service["command"] if isinstance(service["command"], list) else [service["command"]])
        self.assertIn("V004__create_item_service_database.sql", command)
        self.assertIn("HM_ITEM_DB_PASSWORD", service["environment"])

    def test_schema_init_provisions_the_cart_database(self):
        service = self.services["schema-init"]
        migration_mounts = [
            mount
            for mount in service["volumes"]
            if mount["target"] == "/migrations/V005__create_cart_service_database.sql"
        ]
        self.assertEqual(1, len(migration_mounts))
        self.assertTrue(migration_mounts[0]["read_only"])
        command = " ".join(service["command"] if isinstance(service["command"], list) else [service["command"]])
        self.assertIn("V005__create_cart_service_database.sql", command)
        self.assertIn("HM_CART_DB_PASSWORD", service["environment"])

    def test_schema_init_provisions_the_nacos_database(self):
        service = self.services["schema-init"]
        mounts = [mount for mount in service["volumes"] if mount["target"] == "/migrations/nacos.sql"]
        self.assertEqual(1, len(mounts))
        self.assertTrue(mounts[0]["read_only"])
        command = " ".join(service["command"] if isinstance(service["command"], list) else [service["command"]])
        self.assertIn("nacos.sql", command)
        self.assertIn("NACOS_DB_PASSWORD", service["environment"])

    def test_nacos_uses_mysql_auth_and_waits_for_schema(self):
        service = self.services["nacos"]
        self.assertEqual("nacos/nacos-server:v3.1.1", service["image"])
        self.assertEqual("service_completed_successfully", service["depends_on"]["schema-init"]["condition"])
        self.assertEqual("mysql", service["environment"]["MYSQL_SERVICE_HOST"])
        self.assertEqual("nacos", service["environment"]["MYSQL_SERVICE_DB_NAME"])
        self.assertEqual("nacos_app", service["environment"]["MYSQL_SERVICE_USER"])
        self.assertEqual("true", str(service["environment"]["NACOS_AUTH_ENABLE"]).lower())
        self.assertIn("NACOS_AUTH_TOKEN", service["environment"])
        self.assertIn("NACOS_AUTH_IDENTITY_KEY", service["environment"])
        self.assertIn("NACOS_AUTH_IDENTITY_VALUE", service["environment"])
        self.assertEqual("8080", str(service["environment"]["NACOS_CONSOLE_PORT"]))
        published = {int(port["published"]): int(port["target"]) for port in service["ports"]}
        self.assertIn(8848, published.values())
        self.assertIn(9848, published.values())
        self.assertEqual(8080, published[18048])
        self.assertIn("healthcheck", service)
        healthcheck = " ".join(service["healthcheck"]["test"])
        self.assertIn("/v3/console/health/readiness", healthcheck)

    def test_split_services_build_from_the_root_and_wait_for_schema_and_nacos(self):
        expected = {
            "item-service": ("item-service/Dockerfile", "HM_ITEM_DB_PASSWORD"),
            "cart-service": ("cart-service/Dockerfile", "HM_CART_DB_PASSWORD"),
            "user-service": ("user-service/Dockerfile", "HM_USER_DB_PASSWORD"),
            "trade-service": ("trade-service/Dockerfile", "HM_TRADE_DB_PASSWORD"),
            "pay-service": ("pay-service/Dockerfile", "HM_PAY_DB_PASSWORD"),
        }
        for name, (dockerfile, password_name) in expected.items():
            with self.subTest(service=name):
                service = self.services[name]
                self.assertEqual(
                    "service_completed_successfully",
                    service["depends_on"]["schema-init"]["condition"],
                )
                self.assertEqual("service_healthy", service["depends_on"]["nacos"]["condition"])
                self.assertEqual(
                    dockerfile,
                    service["build"]["dockerfile"].replace("\\", "/"),
                )
                self.assertEqual(ROOT, Path(service["build"]["context"]).resolve())
                self.assertIn(password_name, service["environment"])
                self.assertIn("healthcheck", service)
                self.assertIn(
                    str({"item-service": 8081, "cart-service": 8082, "user-service": 8083,
                         "trade-service": 8084, "pay-service": 8085}[name]),
                    " ".join(service["healthcheck"]["test"]),
                )

    def test_marketing_waits_for_its_infrastructure_and_hmall_owners(self):
        marketing = self.services["marketing-service"]
        self.assertEqual("service_completed_successfully", marketing["depends_on"]["schema-init"]["condition"])
        self.assertEqual("service_healthy", marketing["depends_on"]["redis"]["condition"])
        self.assertEqual("service_healthy", marketing["depends_on"]["item-service"]["condition"])
        self.assertEqual("service_healthy", marketing["depends_on"]["user-service"]["condition"])
        self.assertIn("healthcheck", marketing)
        self.assertTrue(marketing["build"]["dockerfile"].replace("\\", "/").endswith("Dockerfile"))
        self.assertEqual("mysql", marketing["environment"]["MYSQL_HOST"])
        self.assertEqual("redis", marketing["environment"]["REDIS_HOST"])
        self.assertEqual("http://item-service:8081", marketing["environment"]["HMALL_ITEM_BASE_URL"])
        self.assertEqual("http://user-service:8083", marketing["environment"]["HMALL_USER_BASE_URL"])

    def test_nginx_exposes_portal_and_admin(self):
        nginx = self.services["nginx"]
        published = {int(port["published"]): int(port["target"]) for port in nginx["ports"]}
        self.assertEqual(18080, published[18080])
        self.assertEqual(18081, published[18081])
        self.assertEqual("service_healthy", nginx["depends_on"]["hm-gateway"]["condition"])
        self.assertEqual("service_healthy", nginx["depends_on"]["marketing-service"]["condition"])
        self.assertIn("healthcheck", nginx)
        healthcheck = " ".join(nginx["healthcheck"]["test"])
        self.assertIn("127.0.0.1:18080", healthcheck)
        self.assertIn("127.0.0.1:18081/items.html", healthcheck)

    def test_nginx_mounts_the_shared_marketing_login_exception_read_only(self):
        mounts = [
            mount for mount in self.services["nginx"].get("volumes", [])
            if mount["target"] == "/etc/nginx/snippets/marketing-auth-login.conf"
        ]
        self.assertEqual(1, len(mounts))
        self.assertEqual("bind", mounts[0]["type"])
        self.assertEqual(
            ROOT / "nginx" / "snippets" / "marketing-auth-login.conf",
            Path(mounts[0]["source"]),
        )
        self.assertTrue(mounts[0]["read_only"])

    def test_gateway_joins_the_default_stack_and_waits_for_discovery_and_routes(self):
        gateway = self.services["hm-gateway"]
        self.assertEqual("hm-gateway/Dockerfile", gateway["build"]["dockerfile"].replace("\\", "/"))
        self.assertEqual(ROOT, Path(gateway["build"]["context"]).resolve())
        self.assertEqual("service_healthy", gateway["depends_on"]["nacos"]["condition"])
        for dependency in (
            "item-service",
            "cart-service",
            "user-service",
            "trade-service",
            "pay-service",
            "marketing-service",
        ):
            self.assertIn(dependency, gateway["depends_on"])
            self.assertEqual("service_healthy", gateway["depends_on"][dependency]["condition"])
        self.assertEqual("nacos:8848", gateway["environment"]["NACOS_SERVER_ADDR"])
        self.assertIn("healthcheck", gateway)
        self.assertIn("8080", " ".join(gateway["healthcheck"]["test"]))


class MigrationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = MIGRATION_FILE.read_text(encoding="utf-8")
        cls.normalized = re.sub(r"\s+", " ", cls.sql).lower()

    def test_role_column_is_added_idempotently_and_admin_is_seeded(self):
        self.assertRegex(
            self.normalized,
            r"information_schema\.columns.*table_schema\s*=\s*'hmall'.*table_name\s*=\s*'user'.*column_name\s*=\s*'role'",
        )
        self.assertRegex(
            self.normalized,
            r"alter table\s+`?user`?\s+add column\s+`?role`?\s+varchar\(20\)\s+not null\s+default\s+'user'",
        )
        self.assertRegex(self.normalized, r"update\s+`?user`?\s+set\s+`?role`?\s*=\s*'admin'\s+where\s+`?id`?\s*=\s*1")

    def test_outbox_is_created_idempotently(self):
        self.assertRegex(
            self.normalized,
            r"create table if not exists\s+`?order_reward_outbox`?",
        )
        self.assertIn("primary key", self.normalized)
        self.assertIn("unique key", self.normalized)
        self.assertIn("status", self.normalized)
        self.assertIn("payload", self.normalized)

    def test_luckyhub_database_user_and_grant_are_idempotent(self):
        self.assertRegex(self.normalized, r"create database if not exists\s+`?luckyhub`?")
        self.assertIn("create user if not exists", self.normalized)
        self.assertRegex(self.normalized, r"grant\s+all privileges\s+on\s+`?luckyhub`?\.\*")


class ItemDatabaseIsolationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = ITEM_MIGRATION_FILE.read_text(encoding="utf-8")
        cls.normalized = re.sub(r"\s+", " ", cls.sql).lower()

    def test_item_database_and_runtime_user_are_created_idempotently(self):
        self.assertRegex(self.normalized, r"create database if not exists\s+`?hm_item`?")
        self.assertIn("create user if not exists 'hm_item_app'@'%'", self.normalized)
        self.assertIn("alter user 'hm_item_app'@'%'", self.normalized)

    def test_runtime_user_receives_only_item_database_dml_permissions(self):
        self.assertRegex(
            self.normalized,
            r"grant\s+select\s*,\s*insert\s*,\s*update\s*,\s*delete\s+on\s+`?hm_item`?\.\*\s+to\s+'hm_item_app'@'%'",
        )
        self.assertNotRegex(self.normalized, r"grant\s+all privileges.*'hm_item_app'")
        self.assertNotRegex(self.normalized, r"grant.*`?hmall`?\.\*.*'hm_item_app'")

    def test_item_table_is_cloned_and_seeded_without_removing_the_source(self):
        self.assertRegex(
            self.normalized,
            r"create table if not exists\s+`?hm_item`?\.`?item`?\s+like\s+`?hmall`?\.`?item`?",
        )
        self.assertRegex(
            self.normalized,
            r"insert ignore into\s+`?hm_item`?\.`?item`?.*select\s+\*\s+from\s+`?hmall`?\.`?item`?",
        )
        self.assertNotRegex(self.normalized, r"drop table.*`?hmall`?\.`?item`?")

    def test_item_service_uses_its_database_and_runtime_account(self):
        application = read("item-service/src/main/resources/application.yaml")
        dev = read("item-service/src/main/resources/application-dev.yaml")
        local = read("item-service/src/main/resources/application-local.yaml")
        combined = "\n".join([dev, local])
        self.assertIn("optional:nacos:shared.jdbc.yaml", application)
        self.assertNotRegex(application, r"(?m)^\s{2}datasource:\s*$")
        self.assertIn("database: hm_item", combined)
        self.assertIn("username: hm_item_app", combined)
        self.assertNotRegex(application, r"(?m)^\s*username:\s*root\s*$")
        self.assertIn("HM_ITEM_DB_PASSWORD", read(".env.example"))

    def test_local_profile_uses_the_published_docker_mysql_port(self):
        application = read("item-service/src/main/resources/application.yaml")
        local = read("item-service/src/main/resources/application-local.yaml")
        self.assertIn("active: ${SPRING_PROFILES_ACTIVE:local}", application)
        self.assertIn("host: 127.0.0.1", local)
        self.assertIn("port: ${HM_ITEM_DB_PORT:13307}", local)

    def test_local_profile_loads_the_gitignored_root_dotenv_for_idea(self):
        local = read("item-service/src/main/resources/application-local.yaml")
        self.assertIn("optional:file:.env[.properties]", local)
        self.assertIn("optional:file:../.env[.properties]", local)


class CartDatabaseIsolationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = CART_MIGRATION_FILE.read_text(encoding="utf-8")
        cls.normalized = re.sub(r"\s+", " ", cls.sql).lower()

    def test_cart_database_and_runtime_user_are_created_idempotently(self):
        self.assertRegex(self.normalized, r"create database if not exists\s+`?hm_cart`?")
        self.assertIn("create user if not exists 'hm_cart_app'@'%'", self.normalized)
        self.assertIn("alter user 'hm_cart_app'@'%'", self.normalized)

    def test_runtime_user_receives_only_cart_database_dml_permissions(self):
        self.assertRegex(
            self.normalized,
            r"grant\s+select\s*,\s*insert\s*,\s*update\s*,\s*delete\s+on\s+`?hm_cart`?\.\*\s+to\s+'hm_cart_app'@'%'",
        )
        self.assertNotRegex(self.normalized, r"grant\s+all privileges.*'hm_cart_app'")
        self.assertNotRegex(self.normalized, r"grant.*`?hmall`?\.\*.*'hm_cart_app'")

    def test_cart_table_is_cloned_without_removing_the_source(self):
        self.assertRegex(
            self.normalized,
            r"create table if not exists\s+`?hm_cart`?\.`?cart`?\s+like\s+`?hmall`?\.`?cart`?",
        )
        self.assertRegex(
            self.normalized,
            r"insert ignore into\s+`?hm_cart`?\.`?cart`?.*select\s+\*\s+from\s+`?hmall`?\.`?cart`?",
        )
        self.assertNotRegex(self.normalized, r"drop table.*`?hmall`?\.`?cart`?")

    def test_cart_service_uses_its_database_and_runtime_account(self):
        dev = read("cart-service/src/main/resources/application-dev.yaml")
        local = read("cart-service/src/main/resources/application-local.yaml")
        combined = "\n".join([dev, local])
        self.assertIn("database: hm_cart", combined)
        self.assertIn("username: hm_cart_app", combined)
        self.assertNotIn("database: hm_item", combined)
        self.assertIn("HM_CART_DB_PASSWORD", read(".env.example"))
        self.assertIn("port: ${HM_CART_DB_PORT:13307}", local)

    def test_local_profile_loads_the_gitignored_root_dotenv_for_idea(self):
        local = read("cart-service/src/main/resources/application-local.yaml")
        self.assertIn("optional:file:.env[.properties]", local)
        self.assertIn("optional:file:../.env[.properties]", local)


class NacosDatabaseContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = NACOS_SCHEMA_FILE.read_text(encoding="utf-8")
        cls.normalized = re.sub(r"\s+", " ", cls.sql).lower()

    def test_schema_is_repeatable_and_non_destructive(self):
        self.assertRegex(self.normalized, r"create database if not exists\s+`?nacos`?")
        self.assertIn("create table if not exists", self.normalized)
        self.assertNotIn("drop database", self.normalized)
        self.assertNotIn("drop table", self.normalized)
        self.assertNotRegex(self.normalized, r"delete\s+from")

    def test_dedicated_database_user_is_scoped_to_nacos(self):
        self.assertIn("create user if not exists 'nacos_app'@'%'", self.normalized)
        self.assertRegex(self.normalized, r"alter user\s+''nacos_app''@''%''")
        self.assertRegex(self.normalized, r"grant\s+all privileges\s+on\s+`?nacos`?\.\*")
        self.assertNotRegex(self.normalized, r"grant.*\*\.\*.*nacos_app")

    def test_default_console_account_is_seeded_idempotently(self):
        self.assertRegex(self.normalized, r"insert ignore into\s+`?roles`?")
        self.assertRegex(self.normalized, r"insert ignore into\s+`?users`?")

    def test_nacos_31_schema_columns_are_present(self):
        self.assertIn("config_info_gray", self.normalized)
        self.assertIn("publish_type", self.normalized)
        self.assertIn("gray_name", self.normalized)
        self.assertIn("ext_info", self.normalized)

    def test_container_environment_does_not_use_root_or_a_lan_ip(self):
        environment = read("mysql/nacos/custom.env")
        self.assertIn("MYSQL_SERVICE_HOST=mysql", environment)
        self.assertIn("MYSQL_SERVICE_USER=nacos_app", environment)
        self.assertIn("NACOS_AUTH_ENABLE=true", environment)
        self.assertIn("JAVA_TOOL_OPTIONS=-XX:-UseContainerSupport", environment)
        self.assertNotIn("MYSQL_SERVICE_USER=root", environment)
        self.assertNotIn("192.168.", environment)
        self.assertNotRegex(environment, r"(?m)^MYSQL_SERVICE_PASSWORD=")
        example = read(".env.example")
        self.assertIn("NACOS_DB_PASSWORD", example)
        self.assertIn("NACOS_AUTH_TOKEN", example)
        self.assertIn("NACOS_CONSOLE_PORT=18048", example)


class DockerfileContractTest(unittest.TestCase):
    def assert_multistage_java_build(self, path: str, java_version: str):
        dockerfile = read(path).lower()
        self.assertGreaterEqual(len(re.findall(r"(?m)^from\s+", dockerfile)), 2)
        self.assertRegex(dockerfile, rf"from\s+maven:[^\n]*temurin-{java_version}[^\n]*\s+as\s+build")
        self.assertIn(f"from eclipse-temurin:{java_version}-jre", dockerfile)
        self.assertIn("mvn", dockerfile)
        self.assertIn("package", dockerfile)
        self.assertRegex(dockerfile, r"copy\s+--from=build")
        self.assertIn('entrypoint ["java", "-jar"', dockerfile)

    def test_all_java_services_are_built_in_java_17_multistage_images(self):
        for service in ("item", "cart", "user", "trade", "pay"):
            with self.subTest(service=service):
                self.assert_multistage_java_build(
                    f"{service}-service/Dockerfile", "17"
                )
        self.assert_multistage_java_build("mall-backend/hm-service/Dockerfile", "17")
        self.assert_multistage_java_build("marketing-service/Dockerfile", "17")
        self.assert_multistage_java_build("hm-gateway/Dockerfile", "17")

    def test_build_context_excludes_secrets_and_build_products(self):
        dockerignore = read(".dockerignore")
        self.assertRegex(dockerignore, r"(?m)^\.env$")
        self.assertRegex(dockerignore, r"(?m)^\*\*/target$")

    def test_nginx_image_copies_config_and_both_frontends(self):
        dockerfile = read("nginx/Dockerfile").lower()
        self.assertRegex(dockerfile, r"(?m)^from\s+nginx:")
        self.assertIn("copy nginx.conf /etc/nginx/nginx.conf", dockerfile)
        self.assertIn("copy html/hmall-portal", dockerfile)
        self.assertIn("copy html/hmall-admin", dockerfile)

    def test_gateway_packages_only_the_jwt_verification_certificate(self):
        resources = ROOT / "hm-gateway" / "src" / "main" / "resources"
        self.assertTrue((resources / "hmall-public.pem").is_file())
        self.assertFalse((resources / "hmall.jks").exists())
        application = read("hm-gateway/src/main/resources/application.yaml")
        self.assertIn("public-key-location: classpath:hmall-public.pem", application)
        self.assertNotIn("password: hmall123", application)
        self.assertNotRegex(application, r"(?m)^\s+alias:\s+hmall\s*$")


class NginxRoutingContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.conf = read("nginx/nginx.conf")
        cls.normalized = re.sub(r"\s+", " ", cls.conf)

    def test_both_static_sites_are_served(self):
        self.assertIn("listen 18080", self.normalized)
        self.assertIn("listen 18081", self.normalized)
        self.assertIn("/usr/share/nginx/html/hmall-portal", self.conf)
        self.assertIn("/usr/share/nginx/html/hmall-admin", self.conf)

    def test_marketing_routes_keep_api_prefix_after_stripping_marketing_segment(self):
        locations = re.findall(
            r"location\s+\^~\s+/api/marketing/\s*\{([^}]+)\}", self.conf, re.S
        )
        self.assertEqual(2, len(locations))
        for body in locations:
            self.assertIn("set $gateway_backend hm-gateway:8080;", body)
            self.assertRegex(body, r"rewrite\s+\^/api/marketing/\(\.\*\)\$\s+/api/\$1\s+break;")
            self.assertIn("proxy_pass http://$gateway_backend;", body)

    def test_exact_legacy_marketing_login_stays_on_marketing_until_identity_cutover(self):
        servers = re.findall(r"(?ms)^    server\s*\{(.*?)^    \}", self.conf)
        self.assertEqual(2, len(servers))
        for server in servers:
            self.assertEqual(
                1,
                server.count("include /etc/nginx/snippets/marketing-auth-login.conf;"),
            )
        snippet_path = ROOT / "nginx" / "snippets" / "marketing-auth-login.conf"
        self.assertTrue(snippet_path.is_file(), "shared marketing login snippet must exist")
        snippet = read("nginx/snippets/marketing-auth-login.conf")
        locations = re.findall(
            r"location\s*=\s*/api/marketing/auth/login\s*\{([^}]+)\}",
            snippet,
            re.S,
        )
        self.assertEqual(1, len(locations))
        body = locations[0]
        self.assertIn("set $marketing_backend marketing-service:8080;", body)
        self.assertIn("rewrite ^/api/marketing/auth/login$ /api/auth/login break;", body)
        self.assertIn("proxy_pass http://$marketing_backend;", body)

    def test_hmall_api_routes_are_sent_through_the_authenticating_gateway(self):
        self.assertGreaterEqual(
            len(re.findall(r"location\s+\^~\s+/api/", self.conf)),
            4,
        )
        self.assertEqual(
            4,
            len(re.findall(r"proxy_pass\s+http://\$gateway_backend;", self.conf)),
        )
        self.assertGreaterEqual(
            len(re.findall(r"rewrite\s+\^/api/\(\.\*\)\$\s+/\$1\s+break;", self.conf)),
            2,
        )
        self.assertNotIn("proxy_pass http://mall-service:8080", self.conf)
        for upstream in (
            "item-service:8081",
            "cart-service:8082",
            "user-service:8083",
            "trade-service:8084",
            "pay-service:8085",
        ):
            with self.subTest(upstream=upstream):
                self.assertNotIn(f"proxy_pass http://{upstream}", self.conf)

    def test_proxy_upstreams_use_docker_runtime_dns(self):
        self.assertRegex(
            self.conf,
            r"resolver\s+127\.0\.0\.11\s+valid=10s\s+ipv6=off;",
        )
        self.assertEqual(
            0,
            len(re.findall(r"set\s+\$marketing_backend\s+marketing-service:8080;", self.conf)),
        )
        self.assertEqual(
            0,
            len(re.findall(r"proxy_pass\s+http://\$marketing_backend;", self.conf)),
        )
        self.assertEqual(
            4,
            len(re.findall(r"set\s+\$gateway_backend\s+hm-gateway:8080;", self.conf)),
        )
        self.assertEqual(
            4,
            len(re.findall(r"proxy_pass\s+http://\$gateway_backend;", self.conf)),
        )

    def test_runtime_upstream_is_initialized_before_rewrite_breaks(self):
        marketing_location = (
            r"location\s+\^~\s+/api/marketing/\s*\{\s*"
            r"set\s+\$gateway_backend\s+hm-gateway:8080;\s*"
            r"rewrite\s+\^/api/marketing/\(\.\*\)\$\s+/api/\$1\s+break;\s*"
            r"proxy_pass\s+http://\$gateway_backend;"
        )
        gateway_location = (
            r"location\s+\^~\s+/api/\s*\{\s*"
            r"set\s+\$gateway_backend\s+hm-gateway:8080;\s*"
            r"rewrite\s+\^/api/\(\.\*\)\$\s+/\$1\s+break;\s*"
            r"proxy_pass\s+http://\$gateway_backend;"
        )
        self.assertEqual(2, len(re.findall(marketing_location, self.conf)))
        self.assertEqual(2, len(re.findall(gateway_location, self.conf)))

    def test_gateway_keeps_marketing_compatibility_and_callback_rewrite(self):
        application = read("hm-gateway/src/main/resources/application.yaml")
        self.assertRegex(
            application,
            r"(?ms)^\s+- id: marketing-payment-callback-compat\s+"
            r"uri: lb://marketing-service\s+predicates:\s+"
            r"- Path=/api/callbacks/payments,/api/callbacks/payments/\*\*\s+"
            r"filters:\s+- StripPrefix=1\s*$",
        )
        self.assertIn("- POST /api/shipping/callbacks/logistics", application)
        self.assertNotIn("POST /api/auth/login", application)
        self.assertNotIn("POST /callbacks/payments", application)
        self.assertIn("- id: marketing-payment-callback-native", application)
        self.assertIn("Path=/callbacks/payments,/callbacks/payments/**", application)
        legacy = re.search(r"(?ms)^\s+- id: marketing-legacy\s+.*?(?=^\s+- id:|\Z)", application)
        self.assertIsNotNone(legacy)
        self.assertIn("uri: lb://marketing-service", legacy.group(0))
        self.assertIn("Path=/api/**", legacy.group(0))
        for route_id in (
            "item-service",
            "cart-service",
            "user-service",
            "trade-service",
            "pay-service",
            "marketing-payment-callback-native",
        ):
            self.assertLess(application.index(f"id: {route_id}"), legacy.start())
        self.assertLess(
            application.index("id: marketing-payment-callback-compat"),
            application.index("id: marketing-legacy"),
        )


class MarketingHmallAdapterContractTest(unittest.TestCase):
    def test_marketing_local_run_loads_the_root_dotenv_and_compose_password_name(self):
        application = read("marketing-service/src/main/resources/application.yaml")
        self.assertIn("optional:file:.env[.properties]", application)
        self.assertIn("optional:file:../.env[.properties]", application)
        self.assertIn("${MYSQL_PASSWORD:${LUCKYHUB_DB_PASSWORD:}}", application)

    def test_catalog_and_address_adapters_have_independent_owner_urls(self):
        application = read("marketing-service/src/main/resources/application.yaml")
        catalog = read(
            "marketing-service/src/main/java/com/dongqh/luckyhub/integration/hmall/HttpHmallCatalogClient.java"
        )
        address = read(
            "marketing-service/src/main/java/com/dongqh/luckyhub/integration/hmall/HttpHmallAddressClient.java"
        )
        self.assertIn("catalog-base-url: ${HMALL_ITEM_BASE_URL:http://localhost:8081}", application)
        self.assertIn("address-base-url: ${HMALL_USER_BASE_URL:http://localhost:8083}", application)
        self.assertIn("luckyhub.integration.hmall.catalog-base-url", catalog)
        self.assertIn("luckyhub.integration.hmall.address-base-url", address)
        self.assertNotIn("luckyhub.integration.hmall.base-url", catalog)
        self.assertNotIn("luckyhub.integration.hmall.base-url", address)


if __name__ == "__main__":
    unittest.main()
