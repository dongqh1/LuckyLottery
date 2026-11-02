from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
SERVICES = {
    "item-service": ("com.dongqh.item.controller", "黑马商城商品管理接口文档", "hm_item"),
    "cart-service": ("com.dongqh.cart.controller", "黑马商城购物车管理接口文档", "hm_cart"),
    "user-service": ("com.dongqh.user.controller", "黑马商城用户管理接口文档", "hm_user"),
    "trade-service": ("com.dongqh.trade.controller", "黑马商城交易管理接口文档", "hm_trade"),
    "pay-service": ("com.dongqh.pay.controller", "黑马商城支付管理接口文档", "hm_pay"),
}
CONFIG_IMPORTS = (
    "optional:nacos:shared.jdbc.yaml?group=DEFAULT_GROUP&refreshEnabled=true",
    "optional:nacos:shared.logger.yaml?group=DEFAULT_GROUP&refreshEnabled=true",
    "optional:nacos:shared.swagger.yaml?group=DEFAULT_GROUP&refreshEnabled=true",
    "optional:nacos:${spring.application.name}.yaml?group=DEFAULT_GROUP&refreshEnabled=true",
)


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def dependency_artifacts(service: str) -> set[tuple[str, str]]:
    root = ET.parse(ROOT / service / "pom.xml").getroot()
    return {
        (
            dependency.findtext("m:groupId", namespaces=MAVEN_NAMESPACE),
            dependency.findtext("m:artifactId", namespaces=MAVEN_NAMESPACE),
        )
        for dependency in root.findall("m:dependencies/m:dependency", MAVEN_NAMESPACE)
    }


class NacosSharedConfigContractTest(unittest.TestCase):
    def test_each_service_uses_nacos_config_data_dependencies(self):
        required = {
            ("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-config"),
            ("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-discovery"),
        }
        for service in SERVICES:
            with self.subTest(service=service):
                dependencies = dependency_artifacts(service)
                self.assertTrue(required.issubset(dependencies))
                self.assertNotIn(
                    ("org.springframework.cloud", "spring-cloud-starter-bootstrap"),
                    dependencies,
                )
                self.assertFalse(
                    (ROOT / service / "src/main/resources/bootstrap.yaml").exists()
                )

    def test_each_service_application_loads_config_data_imports(self):
        for service in SERVICES:
            with self.subTest(service=service):
                application = read(f"{service}/src/main/resources/application.yaml")
                self.assertRegex(application, rf"(?m)^    name: {re.escape(service)}$")
                for variable in (
                    "SPRING_PROFILES_ACTIVE",
                    "NACOS_SERVER_ADDR",
                    "NACOS_USERNAME",
                    "NACOS_PASSWORD",
                ):
                    self.assertIn(f"${{{variable}:", application)
                self.assertIn("group: DEFAULT_GROUP", application)
                for config_import in CONFIG_IMPORTS:
                    self.assertEqual(1, application.count(config_import))
                self.assertEqual(len(CONFIG_IMPORTS), application.count("optional:nacos:"))

    def test_application_keeps_only_service_specific_configuration(self):
        forbidden_top_level = ("mybatis-plus", "logging", "knife4j")
        for service, (controller_package, title, _) in SERVICES.items():
            with self.subTest(service=service):
                application = read(f"{service}/src/main/resources/application.yaml")
                self.assertNotRegex(application, r"(?m)^  datasource:\s*$")
                for key in forbidden_top_level:
                    self.assertNotRegex(application, rf"(?m)^{re.escape(key)}:\s*$")
                self.assertRegex(application, r"(?m)^hm:\s*$")
                self.assertRegex(application, r"(?m)^  swagger:\s*$")
                self.assertIn(f"title: {title}", application)
                self.assertIn(f"description: {title}", application)
                self.assertIn(f"package: {controller_package}", application)

    def test_profiles_keep_each_service_database_isolation(self):
        for service, (_, _, database) in SERVICES.items():
            for profile in ("local", "dev"):
                with self.subTest(service=service, profile=profile):
                    content = read(
                        f"{service}/src/main/resources/application-{profile}.yaml"
                    )
                    self.assertRegex(content, r"(?m)^hm:\s*$")
                    self.assertRegex(content, r"(?m)^  db:\s*$")
                    self.assertIn(f"database: {database}", content)

    def test_unrelated_services_are_not_opted_into_shared_config(self):
        unrelated_poms = (
            "hm-gateway/pom.xml",
            "hm-security/pom.xml",
            "mall-backend/hm-service/pom.xml",
        )
        for pom in unrelated_poms:
            with self.subTest(pom=pom):
                self.assertNotIn(
                    "spring-cloud-starter-alibaba-nacos-config",
                    read(pom),
                )


if __name__ == "__main__":
    unittest.main()
