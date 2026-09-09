from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


class SplitFrontendApiContractTest(unittest.TestCase):
    def test_item_service_owns_every_search_route_used_by_frontend(self):
        controller_path = ROOT / (
            "item-service/src/main/java/com/dongqh/itemservice/controller/"
            "SearchController.java"
        )
        self.assertTrue(controller_path.is_file())
        controller = controller_path.read_text(encoding="utf-8")
        self.assertIn('@RequestMapping("/search")', controller)
        self.assertIn('@GetMapping("/list")', controller)
        self.assertIn('@GetMapping("/suggestion")', controller)
        self.assertIn('@PostMapping("/filters")', controller)

    def test_user_service_owns_every_user_route_used_by_frontend(self):
        controller = read(
            "user-service/src/main/java/com/dongqh/userservice/controller/UserController.java"
        )
        for route in (
            '@GetMapping("/page")',
            "@PostMapping",
            "@PutMapping",
            '@DeleteMapping("/{id}")',
            '@PutMapping("/status/{id}/{status}")',
        ):
            with self.subTest(route=route):
                self.assertIn(route, controller)


if __name__ == "__main__":
    unittest.main()
