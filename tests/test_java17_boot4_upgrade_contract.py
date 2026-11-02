from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
MODULES = (
    "hm-security", "mall-backend", "item-service", "hm-api", "hm-gateway",
    "cart-service", "user-service", "trade-service", "pay-service",
    "lottery-service", "points-service", "coupon-service", "membership-service",
    "fulfillment-service",
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
ROOT_MANAGED_DEPENDENCIES = {
    ("org.springframework.cloud", "spring-cloud-dependencies"):
        ("${spring-cloud.version}", "pom", "import"),
    ("com.alibaba.cloud", "spring-cloud-alibaba-dependencies"):
        ("${spring-cloud-alibaba.version}", "pom", "import"),
    ("com.baomidou", "mybatis-plus-spring-boot4-starter"):
        ("${mybatis-plus.version}", None, None),
    ("com.baomidou", "mybatis-plus-jsqlparser"):
        ("${mybatis-plus.version}", None, None),
    ("org.springdoc", "springdoc-openapi-starter-webmvc-ui"):
        ("${springdoc.version}", None, None),
}
ROOT_PARENT = ("com.dongqh", "hmall-lottery", "1.0.0-SNAPSHOT", "../pom.xml")
HMALL_PARENT = ("com.heima", "hmall", "1.0.0", "../mall-backend/pom.xml")
MODULE_PARENT_CHAINS = {
    "hm-security/pom.xml": ROOT_PARENT,
    "mall-backend/pom.xml": ROOT_PARENT,
    "item-service/pom.xml": HMALL_PARENT,
    "hm-api/pom.xml": HMALL_PARENT,
    "hm-gateway/pom.xml": HMALL_PARENT,
    "cart-service/pom.xml": HMALL_PARENT,
    "user-service/pom.xml": HMALL_PARENT,
    "trade-service/pom.xml": HMALL_PARENT,
    "pay-service/pom.xml": HMALL_PARENT,
    "lottery-service/pom.xml": ROOT_PARENT,
    "points-service/pom.xml": ROOT_PARENT,
    "coupon-service/pom.xml": ROOT_PARENT,
    "membership-service/pom.xml": ROOT_PARENT,
    "fulfillment-service/pom.xml": ROOT_PARENT,
    "mall-backend/hm-common/pom.xml": ("com.heima", "hmall", "1.0.0", "../pom.xml"),
    "mall-backend/hm-service/pom.xml": ("com.heima", "hmall", "1.0.0", "../pom.xml"),
}
AUTO_CONFIGURATIONS = (
    "com.hmall.common.config.MyBatisConfig",
    "com.hmall.common.config.MvcConfig",
    "com.hmall.common.config.JsonConfig",
    "com.hmall.common.swagger.OpenApiAutoConfiguration",
)


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def active_paths(pattern: str):
    return (
        path for path in ROOT.rglob(pattern)
        if path.relative_to(ROOT).parts[0] not in SCRATCH_DIRS
    )


def parent_coordinates(project):
    parent = project.find("m:parent", NS)
    if parent is None:
        return None
    relative_path = parent.find("m:relativePath", NS)
    resolved_relative_path = "../pom.xml" if relative_path is None else (relative_path.text or "").strip()
    return (
        parent.findtext("m:groupId", namespaces=NS),
        parent.findtext("m:artifactId", namespaces=NS),
        parent.findtext("m:version", namespaces=NS),
        resolved_relative_path,
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

    def test_module_parent_chain_is_exact(self):
        violations = []
        for path, expected in MODULE_PARENT_CHAINS.items():
            actual = parent_coordinates(ET.parse(ROOT / path).getroot())
            if actual != expected:
                violations.append(f"{path}: expected parent {expected}, found {actual}")
        self.assertEqual([], violations)

    def test_platform_versions_are_single_sourced(self):
        violations = []
        root = ET.parse(ROOT / "pom.xml").getroot()
        for name, expected in ROOT_PLATFORM_PROPERTIES.items():
            actual = root.findtext(f"m:properties/m:{name}", namespaces=NS)
            if actual != expected:
                violations.append(f"pom.xml: expected {name}={expected}, found {actual}")

        managed_dependencies = root.findall("m:dependencyManagement/m:dependencies/m:dependency", NS)
        managed_coordinates = [
            (
                dependency.findtext("m:groupId", namespaces=NS),
                dependency.findtext("m:artifactId", namespaces=NS),
            )
            for dependency in managed_dependencies
        ]
        if len(managed_coordinates) != len(set(managed_coordinates)):
            violations.append("pom.xml: dependencyManagement contains duplicate coordinates")
        for coordinates, expected in ROOT_MANAGED_DEPENDENCIES.items():
            matches = [
                dependency for dependency, actual in zip(managed_dependencies, managed_coordinates)
                if actual == coordinates
            ]
            if len(matches) != 1:
                violations.append(
                    f"pom.xml: dependencyManagement must contain {coordinates} exactly once; found {len(matches)}"
                )
                continue
            dependency = matches[0]
            actual = (
                dependency.findtext("m:version", namespaces=NS),
                dependency.findtext("m:type", namespaces=NS),
                dependency.findtext("m:scope", namespaces=NS),
            )
            if actual != expected:
                violations.append(f"pom.xml: managed dependency {coordinates} expected {expected}, found {actual}")

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
            "lottery-service/Dockerfile", "points-service/Dockerfile",
            "coupon-service/Dockerfile", "membership-service/Dockerfile",
            "fulfillment-service/Dockerfile",
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
        registrations = []
        for line in source.splitlines():
            registration = line.split("#", 1)[0].strip()
            if registration:
                registrations.append(registration)
        self.assertEqual(list(AUTO_CONFIGURATIONS), registrations)


if __name__ == "__main__":
    unittest.main()
