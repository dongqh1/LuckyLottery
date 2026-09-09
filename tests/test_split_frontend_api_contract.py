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

    def test_trade_and_pay_own_the_status_routes_used_by_checkout(self):
        trade = read(
            "trade-service/src/main/java/com/dongqh/tradeservice/controller/OrderController.java"
        )
        pay = read(
            "pay-service/src/main/java/com/dongqh/payservice/controller/PayController.java"
        )
        checkout = read("nginx/html/hmall-portal/pay.html")
        self.assertIn('@GetMapping("/{orderId}/status")', trade)
        self.assertIn('@GetMapping("/{payOrderId}/status")', pay)
        self.assertIn('"/orders/" + this.order.id + "/status"', checkout)
        self.assertIn('"/pay-orders/" + this.payOrderNo + "/status"', checkout)
        self.assertNotIn('"/order/status/" + id', checkout)
        self.assertNotIn('"/pay/status/" + id', checkout)


if __name__ == "__main__":
    unittest.main()
