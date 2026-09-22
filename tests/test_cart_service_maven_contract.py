from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def parse_pom(relative_path: str) -> ET.Element:
    return ET.parse(ROOT / relative_path).getroot()


def value(element: ET.Element, path: str) -> str | None:
    node = element.find(path, NS)
    return node.text.strip() if node is not None and node.text else None


class CartServiceMavenContractTest(unittest.TestCase):
    def test_root_reactor_contains_cart_service(self):
        root = parse_pom("pom.xml")
        modules = {node.text.strip() for node in root.findall("m:modules/m:module", NS)}
        self.assertIn("cart-service", modules)

    def test_cart_service_matches_the_current_hmall_baseline(self):
        root = parse_pom("pom.xml")
        cart = parse_pom("cart-service/pom.xml")
        parent = cart.find("m:parent", NS)
        self.assertIsNotNone(parent)
        self.assertEqual("com.heima", value(parent, "m:groupId"))
        self.assertEqual("hmall", value(parent, "m:artifactId"))
        self.assertEqual("1.0.0", value(parent, "m:version"))
        self.assertEqual("../mall-backend/pom.xml", value(parent, "m:relativePath"))
        self.assertEqual("17", value(root, "m:properties/m:java.version"))
        self.assertEqual("3.0.3", value(root, "m:properties/m:springdoc.version"))

        dependencies = {
            (value(node, "m:groupId"), value(node, "m:artifactId")): node
            for node in cart.findall("m:dependencies/m:dependency", NS)
        }
        expected = {
            ("com.heima", "hm-common"),
            ("com.heima", "hm-api"),
            ("org.springframework.boot", "spring-boot-starter-webmvc"),
            ("org.springdoc", "springdoc-openapi-starter-webmvc-ui"),
            ("com.mysql", "mysql-connector-j"),
            ("com.baomidou", "mybatis-plus-spring-boot4-starter"),
            ("com.baomidou", "mybatis-plus-jsqlparser"),
            ("com.h2database", "h2"),
        }
        self.assertTrue(expected.issubset(dependencies))
        self.assertEqual("test", value(dependencies[("com.h2database", "h2")], "m:scope"))


if __name__ == "__main__":
    unittest.main()
