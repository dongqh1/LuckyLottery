from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


class PaySuccessRabbitMqContractTest(unittest.TestCase):
    def test_pay_publishes_one_typed_event_without_duplicate_feign_call(self):
        source = read(
            "pay-service/src/main/java/com/dongqh/payservice/service/impl/"
            "PayOrderServiceImpl.java"
        )

        self.assertIn("new PaySuccessMessage(", source)
        self.assertIn("PayOutboxService", source)
        self.assertIn("payOutboxService.append", source)
        self.assertNotIn("rabbitTemplate.convertAndSend", source)
        self.assertNotIn("couponCheckoutClient.use", source)
        self.assertNotIn("tradeClient.markOrderPaySuccess(", source)

    def test_boot4_amqp_converter_uses_jackson3_and_amqp_interface(self):
        source = read(
            "mall-backend/hm-common/src/main/java/com/hmall/common/config/MqConfig.java"
        )

        self.assertIn(
            "org.springframework.amqp.support.converter.JacksonJsonMessageConverter",
            source,
        )
        self.assertIn(
            "org.springframework.amqp.support.converter.MessageConverter",
            source,
        )
        self.assertNotIn("Jackson2JsonMessageConverter", source)
        self.assertNotIn("org.springframework.messaging.converter.MessageConverter", source)


if __name__ == "__main__":
    unittest.main()
