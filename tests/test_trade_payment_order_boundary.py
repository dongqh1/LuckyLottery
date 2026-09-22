from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class TradePaymentOrderBoundaryTest(unittest.TestCase):
    def read(self, relative_path: str) -> str:
        return (ROOT / relative_path).read_text(encoding="utf-8")

    def test_hm_api_defines_payment_snapshot_without_persistence_annotations(self):
        dto_path = ROOT / "hm-api/src/main/java/com/dongqh/hmapi/dto/OrderPaymentDTO.java"
        self.assertTrue(dto_path.is_file())
        dto = dto_path.read_text(encoding="utf-8")
        for field in ("id", "userId", "status", "totalFee", "couponId"):
            self.assertRegex(dto, rf"private\s+\w+\s+{field};")
        self.assertNotIn("com.baomidou.mybatisplus", dto)
        self.assertNotIn("@TableName", dto)

    def test_trade_client_exposes_snapshot_and_guarded_success_operations(self):
        client = self.read("hm-api/src/main/java/com/dongqh/hmapi/client/TradeClient.java")
        self.assertIn('@FeignClient("trade-service")', client)
        self.assertIn('@GetMapping("/orders/{orderId}/payment")', client)
        self.assertIn("OrderPaymentDTO queryOrderForPayment(", client)
        self.assertIn('@PutMapping("/orders/{orderId}/pay-success")', client)
        self.assertIn("void markOrderPaySuccess(", client)
        self.assertIn('@RequestParam("userId") Long userId', client)
        self.assertIn('@RequestParam("amount") Integer amount', client)

    def test_trade_service_owns_snapshot_and_conditional_order_update(self):
        controller = self.read(
            "trade-service/src/main/java/com/dongqh/tradeservice/controller/OrderController.java"
        )
        interface = self.read(
            "trade-service/src/main/java/com/dongqh/tradeservice/service/IOrderService.java"
        )
        implementation = self.read(
            "trade-service/src/main/java/com/dongqh/tradeservice/service/impl/OrderServiceImpl.java"
        )

        self.assertIn('@GetMapping("/{orderId}/payment")', controller)
        self.assertIn("orderService.queryOrderForPayment(orderId, userId)", controller)
        self.assertIn('@PutMapping("/{orderId}/pay-success")', controller)
        self.assertIn("orderService.markOrderPaySuccess(orderId, userId, amount);", controller)
        self.assertIn(
            "OrderPaymentDTO queryOrderForPayment(Long orderId, Long userId);", interface
        )
        self.assertIn(
            "void markOrderPaySuccess(Long orderId, Long userId, Integer amount);",
            interface,
        )
        self.assertIn("public OrderPaymentDTO queryOrderForPayment(", implementation)
        self.assertIn(".eq(Order::getUserId, userId)", implementation)
        self.assertIn(".eq(Order::getTotalFee, amount)", implementation)
        self.assertIn(".eq(Order::getStatus, 1)", implementation)

    def test_pay_service_uses_dto_and_never_trade_order_po(self):
        implementation = self.read(
            "pay-service/src/main/java/com/dongqh/payservice/service/impl/PayOrderServiceImpl.java"
        )
        self.assertIn("import com.dongqh.hmapi.client.TradeClient;", implementation)
        self.assertIn("import com.dongqh.hmapi.dto.OrderPaymentDTO;", implementation)
        self.assertIn("private final TradeClient tradeClient;", implementation)
        self.assertIn("tradeClient.queryOrderForPayment(", implementation)
        self.assertIn("tradeClient.markOrderPaySuccess(", implementation)
        self.assertNotIn("lockOrder(", implementation)
        self.assertNotIn("orderService.lambdaUpdate", implementation)
        self.assertIsNone(re.search(r"\bOrder\b", implementation))

    def test_user_client_exposes_existing_balance_deduction_endpoint(self):
        client = self.read("hm-api/src/main/java/com/dongqh/hmapi/client/UserClient.java")
        self.assertIn('@PutMapping("/users/money/deduct")', client)
        self.assertIn("void deductMoney(", client)

    def test_pay_service_has_no_trade_service_package_imports(self):
        java_root = ROOT / "pay-service/src/main/java"
        offenders = []
        for path in java_root.rglob("*.java"):
            if "com.dongqh.tradeservice" in path.read_text(encoding="utf-8"):
                offenders.append(str(path.relative_to(ROOT)))
        self.assertEqual([], offenders)


if __name__ == "__main__":
    unittest.main()
