import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
NEW_SERVICES = {
    "lottery-service": ("lotteryservice", "LotteryServiceApplication"),
    "points-service": ("pointsservice", "PointsServiceApplication"),
    "coupon-service": ("couponservice", "CouponServiceApplication"),
    "membership-service": ("membershipservice", "MembershipServiceApplication"),
    "fulfillment-service": ("fulfillmentservice", "FulfillmentServiceApplication"),
}
RUNTIME_NAMES = {"fulfillment-service": "logistics-service"}


class MarketingServiceModuleScaffoldingTests(unittest.TestCase):
    def test_all_five_marketing_domains_are_reactor_modules_with_isolated_bootstrap(self):
        root_pom = ET.parse(ROOT / "pom.xml").getroot()
        reactor_modules = {
            node.text.strip()
            for node in root_pom.findall("m:modules/m:module", NS)
            if node.text
        }

        for module, (package, application_class) in NEW_SERVICES.items():
            with self.subTest(module=module):
                self.assertIn(module, reactor_modules)
                module_root = ROOT / module
                pom_path = module_root / "pom.xml"
                source_path = (
                    module_root
                    / "src"
                    / "main"
                    / "java"
                    / "com"
                    / "dongqh"
                    / package
                    / f"{application_class}.java"
                )
                application_path = module_root / "src" / "main" / "resources" / "application.yml"
                self.assertTrue(pom_path.is_file(), f"Missing {pom_path}")
                self.assertTrue(source_path.is_file(), f"Missing {source_path}")
                self.assertTrue(application_path.is_file(), f"Missing {application_path}")

                module_pom = ET.parse(pom_path).getroot()
                parent = module_pom.find("m:parent", NS)
                self.assertIsNotNone(parent)
                self.assertEqual("com.dongqh", parent.findtext("m:groupId", namespaces=NS))
                self.assertEqual("hmall-lottery", parent.findtext("m:artifactId", namespaces=NS))
                self.assertEqual(module, module_pom.findtext("m:artifactId", namespaces=NS))
                dependency_artifacts = {
                    node.text.strip()
                    for node in module_pom.findall("m:dependencies/m:dependency/m:artifactId", NS)
                    if node.text
                }
                self.assertNotIn("marketing-service", dependency_artifacts)

                source = source_path.read_text(encoding="utf-8")
                self.assertIn(f"package com.dongqh.{package};", source)
                self.assertIn("@SpringBootApplication", source)
                self.assertIn(f'com.dongqh.{package}', source)
                self.assertIn(f"class {application_class}", source)

                application_config = application_path.read_text(encoding="utf-8")
                self.assertIn(f"name: {RUNTIME_NAMES.get(module, module)}", application_config)
                self.assertNotRegex(
                    application_config,
                    r"(?im)^\s*(password|secret|token)\s*:\s*(?!\$\{)[^\s#]+",
                )


if __name__ == "__main__":
    unittest.main()
