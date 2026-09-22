from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def parse_pom(path: str) -> ET.Element:
    return ET.parse(ROOT / path).getroot()


def value(node: ET.Element, path: str) -> str | None:
    found = node.find(path, NS)
    return found.text.strip() if found is not None and found.text else None


class HmApiContractTest(unittest.TestCase):
    def test_reactor_contains_hm_api_before_cart(self):
        modules = [node.text.strip() for node in parse_pom("pom.xml").findall("m:modules/m:module", NS)]
        self.assertIn("hm-api", modules)
        self.assertLess(modules.index("hm-api"), modules.index("cart-service"))

    def test_hm_api_inherits_the_java_17_hmall_baseline(self):
        root = parse_pom("pom.xml")
        api = parse_pom("hm-api/pom.xml")
        parent = api.find("m:parent", NS)
        self.assertIsNotNone(parent)
        self.assertEqual("com.heima", value(parent, "m:groupId"))
        self.assertEqual("hmall", value(parent, "m:artifactId"))
        self.assertEqual("1.0.0", value(parent, "m:version"))
        self.assertEqual("../mall-backend/pom.xml", value(parent, "m:relativePath"))
        self.assertEqual("hm-api", value(api, "m:artifactId"))
        self.assertEqual("17", value(root, "m:properties/m:java.version"))

    def test_hm_api_is_a_non_runnable_openfeign_contract_jar(self):
        api = parse_pom("hm-api/pom.xml")
        dependencies = {
            (value(node, "m:groupId"), value(node, "m:artifactId"))
            for node in api.findall("m:dependencies/m:dependency", NS)
        }
        self.assertEqual(
            {
                ("org.springframework.cloud", "spring-cloud-starter-openfeign"),
                ("com.heima", "hm-common"),
            },
            dependencies,
        )
        self.assertIsNone(api.find("m:build/m:plugins/m:plugin", NS))
        self.assertFalse((ROOT / "hm-api/src/main/java/com/dongqh/hmapi/HmApiApplication.java").exists())
        self.assertFalse((ROOT / "hm-api/src/main/resources/application.yaml").exists())
        self.assertFalse((ROOT / "hm-api/src/test/java/com/dongqh/hmapi/HmApiApplicationTests.java").exists())

    def test_contract_is_self_contained_and_has_no_swagger_dependency(self):
        client = (ROOT / "hm-api/src/main/java/com/dongqh/hmapi/client/ItemClient.java").read_text("utf-8")
        dto = (ROOT / "hm-api/src/main/java/com/dongqh/hmapi/dto/ItemDTO.java").read_text("utf-8")
        self.assertIn("com.dongqh.hmapi.dto.ItemDTO", client)
        self.assertNotIn("cartservice", client)
        self.assertNotIn("io.swagger", dto)

    def test_no_contract_source_depends_on_swagger(self):
        java_root = ROOT / "hm-api/src/main/java"
        offenders = [
            str(path.relative_to(ROOT))
            for path in java_root.rglob("*.java")
            if "io.swagger" in path.read_text(encoding="utf-8")
        ]
        self.assertEqual([], offenders)

    def test_cart_and_user_clients_use_service_owned_paths(self):
        cart = (ROOT / "hm-api/src/main/java/com/dongqh/hmapi/client/CartClient.java").read_text("utf-8")
        user = (ROOT / "hm-api/src/main/java/com/dongqh/hmapi/client/UserClient.java").read_text("utf-8")
        self.assertIn('@DeleteMapping("/carts")', cart)
        self.assertIn('@GetMapping("/addresses/{addressId}")', user)

    def test_cart_consumes_hm_api_without_duplicate_contracts(self):
        cart = parse_pom("cart-service/pom.xml")
        dependencies = {
            (value(node, "m:groupId"), value(node, "m:artifactId"))
            for node in cart.findall("m:dependencies/m:dependency", NS)
        }
        self.assertIn(("com.heima", "hm-api"), dependencies)
        self.assertFalse((ROOT / "cart-service/src/main/java/com/dongqh/cartservice/client/ItemClient.java").exists())
        self.assertFalse((ROOT / "cart-service/src/main/java/com/dongqh/cartservice/domain/dto/ItemDTO.java").exists())

        application = (ROOT / "cart-service/src/main/java/com/dongqh/cartservice/CartServiceApplication.java").read_text("utf-8")
        service = (ROOT / "cart-service/src/main/java/com/dongqh/cartservice/service/impl/CartServiceImpl.java").read_text("utf-8")
        test = (ROOT / "cart-service/src/test/java/com/dongqh/cartservice/CartServiceApplicationTests.java").read_text("utf-8")
        self.assertIn('basePackages = "com.dongqh.hmapi.client"', application)
        self.assertIn("com.dongqh.hmapi.client.ItemClient", service)
        self.assertIn("com.dongqh.hmapi.dto.ItemDTO", service)
        self.assertIn("com.dongqh.hmapi.client.ItemClient", test)
        self.assertIn("com.dongqh.hmapi.dto.ItemDTO", test)


if __name__ == "__main__":
    unittest.main()
