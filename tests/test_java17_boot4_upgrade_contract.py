from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
MODULES = (
    "mall-backend", "item-service", "hm-api", "cart-service",
    "user-service", "trade-service", "pay-service", "marketing-service",
    "hm-gateway",
)
SCRATCH_DIRS = {".codex-progress", ".superpowers"}


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def active_paths(pattern: str):
    return (
        path for path in ROOT.rglob(pattern)
        if path.relative_to(ROOT).parts[0] not in SCRATCH_DIRS
    )


class Java17Boot4UpgradeContractTest(unittest.TestCase):
    def test_root_owns_the_platform_and_all_modules(self):
        root = ET.parse(ROOT / "pom.xml").getroot()
        parent = root.find("m:parent", NS)
        self.assertEqual("org.springframework.boot", parent.findtext("m:groupId", namespaces=NS))
        self.assertEqual("spring-boot-starter-parent", parent.findtext("m:artifactId", namespaces=NS))
        self.assertEqual("4.0.8", parent.findtext("m:version", namespaces=NS))
        self.assertEqual("17", root.findtext("m:properties/m:java.version", namespaces=NS))
        modules = [node.text.strip() for node in root.findall("m:modules/m:module", NS)]
        self.assertEqual(set(MODULES), set(modules))

    def test_platform_versions_are_single_sourced(self):
        root = read("pom.xml")
        self.assertIn("<spring-cloud.version>2025.1.0</spring-cloud.version>", root)
        self.assertIn("<spring-cloud-alibaba.version>2025.1.0.0</spring-cloud-alibaba.version>", root)
        self.assertIn("<mybatis-plus.version>3.5.17</mybatis-plus.version>", root)
        self.assertIn("<springdoc.version>3.0.3</springdoc.version>", root)
        for pom in active_paths("pom.xml"):
            if pom == ROOT / "pom.xml":
                continue
            source = pom.read_text(encoding="utf-8")
            self.assertNotRegex(source, r"<java.version>11</java.version>", str(pom))
            self.assertNotIn("mybatis-plus-boot-starter", source, str(pom))
            self.assertNotIn("knife4j-openapi2-spring-boot-starter", source, str(pom))
            self.assertNotIn("spring-cloud-starter-bootstrap", source, str(pom))

    def test_java_ee_imports_use_jakarta(self):
        offenders = []
        for java in active_paths("*.java"):
            source = java.read_text(encoding="utf-8")
            if re.search(r"import javax\.(servlet|validation)\.", source):
                offenders.append(str(java.relative_to(ROOT)))
        self.assertEqual([], offenders)

    def test_all_java_dockerfiles_use_temurin_17(self):
        dockerfiles = [
            "mall-backend/hm-service/Dockerfile", "item-service/Dockerfile",
            "cart-service/Dockerfile", "user-service/Dockerfile",
            "trade-service/Dockerfile", "pay-service/Dockerfile",
            "marketing-service/Dockerfile",
        ]
        for path in dockerfiles:
            with self.subTest(path=path):
                source = read(path)
                self.assertIn("eclipse-temurin-17", source)
                self.assertIn("eclipse-temurin:17-jre", source)
                self.assertNotIn("temurin-11", source)

    def test_boot4_auto_configuration_registration_exists(self):
        imports = "mall-backend/hm-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        self.assertTrue((ROOT / imports).is_file())
        source = read(imports)
        for name in ("MyBatisConfig", "MvcConfig", "JsonConfig", "OpenApiAutoConfiguration"):
            self.assertIn(name, source)


if __name__ == "__main__":
    unittest.main()
