from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class PayOrderBusinessBoundaryTest(unittest.TestCase):
    def read(self, relative_path: str) -> str:
        return (ROOT / relative_path).read_text(encoding="utf-8")

    def test_hm_api_exposes_business_operation_instead_of_payment_po(self):
        client_path = ROOT / "hm-api/src/main/java/com/dongqh/hmapi/client/PayClient.java"
        self.assertTrue(client_path.is_file())
        client = client_path.read_text(encoding="utf-8")
        self.assertIn('@FeignClient("pay-service")', client)
        self.assertIn(
            '@PutMapping("/pay-orders/biz/{orderId}/pending-amount")', client
        )
        self.assertIn("void syncPendingAmount(", client)
        self.assertIn('@PathVariable("orderId") Long orderId', client)
        self.assertIn('@RequestParam("userId") Long userId', client)
        self.assertIn('@RequestParam("amount") Integer amount', client)

    def test_trade_service_has_no_payment_persistence_dependency(self):
        service = self.read(
            "trade-service/src/main/java/com/dongqh/trade/service/impl/OrderServiceImpl.java"
        )
        self.assertIn("import com.dongqh.hmapi.client.PayClient;", service)
        self.assertIn("private final PayClient payClient;", service)
        self.assertIn("PayClient payClient", service)
        self.assertIn("this.payClient = payClient;", service)
        self.assertIn(
            "payClient.syncPendingAmount(orderId, order.getUserId(), order.getTotalFee());",
            service,
        )
        for forbidden in ("PayOrderMapper", "PayOrder", "PayStatus", "payOrderMapper"):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, service)

    def test_pay_controller_delegates_pending_amount_sync(self):
        controller = self.read(
            "pay-service/src/main/java/com/dongqh/pay/controller/PayController.java"
        )
        self.assertIn('@PutMapping("/biz/{orderId}/pending-amount")', controller)
        self.assertIn("public void syncPendingAmount(", controller)
        self.assertIn(
            "payOrderService.syncPendingAmount(orderId, userId, amount);", controller
        )

    def test_pay_service_owns_lock_validation_and_update(self):
        interface = self.read(
            "pay-service/src/main/java/com/dongqh/pay/service/IPayOrderService.java"
        )
        implementation = self.read(
            "pay-service/src/main/java/com/dongqh/pay/service/impl/PayOrderServiceImpl.java"
        )
        mapper = self.read(
            "pay-service/src/main/java/com/dongqh/pay/mapper/PayOrderMapper.java"
        )

        self.assertIn(
            "void syncPendingAmount(Long orderId, Long userId, Integer amount);",
            interface,
        )
        self.assertIn(
            "public void syncPendingAmount(Long orderId, Long userId, Integer amount)",
            implementation,
        )
        self.assertIn('.eq("biz_order_no", orderId)', implementation)
        self.assertIn('.last("FOR UPDATE")', implementation)
        self.assertIn("PayStatus.NOT_COMMIT", implementation)
        self.assertIn("PayStatus.WAIT_BUYER_PAY", implementation)
        self.assertIn("payment.setAmount(amount);", implementation)
        self.assertIn("updateById(payment)", implementation)
        self.assertIn("BaseMapper<PayOrder>", mapper)

    def test_service_applications_enable_feign_and_scan_local_mappers(self):
        trade_application = self.read(
            "trade-service/src/main/java/com/dongqh/trade/TradeServiceApplication.java"
        )
        pay_application = self.read(
            "pay-service/src/main/java/com/dongqh/pay/PayServiceApplication.java"
        )

        self.assertIn('@MapperScan("com.dongqh.trade.mapper")', trade_application)
        self.assertIn('basePackages = "com.dongqh.hmapi.client"', trade_application)
        self.assertIn('@MapperScan("com.dongqh.pay.mapper")', pay_application)
        self.assertIn('basePackages = "com.dongqh.hmapi.client"', pay_application)


if __name__ == "__main__":
    unittest.main()
