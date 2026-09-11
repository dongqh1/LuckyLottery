from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
MODULES = (
    "mall-backend", "item-service", "hm-api", "hm-gateway", "cart-service",
    "user-service", "trade-service", "pay-service", "marketing-service",
)
SCRATCH_DIRS = {".codex-progress", ".superpowers"}
ROOT_PLATFORM_PROPERTIES = {
    "java.version": "17",
    "maven.compiler.release": "17",
    "spring-cloud.version": "2025.1.0",
    "spring-cloud-alibaba.version": "2025.1.0.0",
    "mybatis-plus.version": "3.5.17",
    "springdoc.version": "3.0.3",
}
CHILD_PLATFORM_PROPERTIES = (
    *ROOT_PLATFORM_PROPERTIES,
    "maven.compiler.source",
    "maven.compiler.target",
)
MANAGED_DEPENDENCY_GROUPS = {
    "org.springframework.boot",
    "org.springframework.cloud",
    "com.alibaba.cloud",
    "com.baomidou",
    "org.springdoc",
}
MANAGED_PLUGINS = {
    ("org.springframework.boot", "spring-boot-maven-plugin"),
    ("org.apache.maven.plugins", "maven-compiler-plugin"),
}


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
        self.assertIsNotNone(parent, "root pom.xml must declare the Spring Boot parent")
        self.assertEqual("org.springframework.boot", parent.findtext("m:groupId", namespaces=NS))
        self.assertEqual("spring-boot-starter-parent", parent.findtext("m:artifactId", namespaces=NS))
        self.assertEqual("4.0.8", parent.findtext("m:version", namespaces=NS))
        self.assertEqual("17", root.findtext("m:properties/m:java.version", namespaces=NS))
        modules = [node.text.strip() for node in root.findall("m:modules/m:module", NS)]
        self.assertEqual(len(modules), len(set(modules)), "root pom.xml contains duplicate modules")
        self.assertEqual(list(MODULES), modules)

    def test_platform_versions_are_single_sourced(self):
        violations = []
        for pom in active_paths("pom.xml"):
            if pom == ROOT / "pom.xml":
                continue
            relative = pom.relative_to(ROOT)
            child = ET.parse(pom).getroot()
            parent = child.find("m:parent", NS)
            if parent is not None and parent.findtext("m:groupId", namespaces=NS) == "org.springframework.boot":
                violations.append(f"{relative}: inherits the Spring Boot parent directly")
            properties = child.find("m:properties", NS)
            if properties is not None:
                for name in CHILD_PLATFORM_PROPERTIES:
                    if properties.find(f"m:{name}", NS) is not None:
                        violations.append(f"{relative}: redeclares platform property {name}")
            for dependency in child.findall(".//m:dependency", NS):
                group_id = dependency.findtext("m:groupId", namespaces=NS)
                artifact_id = dependency.findtext("m:artifactId", namespaces=NS)
                version = dependency.findtext("m:version", namespaces=NS)
                if group_id in MANAGED_DEPENDENCY_GROUPS and version is not None:
                    violations.append(
                        f"{relative}: dependency {group_id}:{artifact_id} hardcodes version {version.strip()}"
                    )
            for plugin in child.findall(".//m:plugin", NS):
                group_id = plugin.findtext("m:groupId", default="org.apache.maven.plugins", namespaces=NS)
                artifact_id = plugin.findtext("m:artifactId", namespaces=NS)
                version = plugin.findtext("m:version", namespaces=NS)
                if (group_id, artifact_id) in MANAGED_PLUGINS and version is not None:
                    violations.append(
                        f"{relative}: plugin {group_id}:{artifact_id} hardcodes version {version.strip()}"
                    )
            source = pom.read_text(encoding="utf-8")
            for obsolete in (
                "mybatis-plus-boot-starter",
                "knife4j-openapi2-spring-boot-starter",
                "spring-cloud-starter-bootstrap",
            ):
                if obsolete in source:
                    violations.append(f"{relative}: contains obsolete dependency {obsolete}")
        self.assertEqual([], violations)

        root = ET.parse(ROOT / "pom.xml").getroot()
        for name, expected in ROOT_PLATFORM_PROPERTIES.items():
            self.assertEqual(
                expected,
                root.findtext(f"m:properties/m:{name}", namespaces=NS),
                f"root pom.xml must own {name}",
            )

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
