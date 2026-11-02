from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_payment_success_event_contains_reliable_delivery_snapshot():
    source = read("hm-api/src/main/java/com/dongqh/hmapi/dto/PaySuccessMessage.java")
    for field in ("String eventId", "Long orderId", "Long userId", "Integer amount",
                  "Long couponId", "LocalDateTime paidAt"):
        assert field in source


def test_pay_flow_uses_outbox_instead_of_direct_rabbit_or_coupon_http():
    source = read(
        "pay-service/src/main/java/com/dongqh/pay/service/impl/PayOrderServiceImpl.java"
    )
    assert "PayOutboxService" in source
    assert "rabbitTemplate.convertAndSend" not in source
    assert "couponCheckoutClient.use" not in source


def test_pay_outbox_schema_and_topology_are_declared():
    migration = read("mysql/migrations/V010__add_pay_message_outbox.sql")
    assert "pay_message_outbox" in migration
    assert "uk_pay_outbox_event_id" in migration
    assert "idx_pay_outbox_relay" in migration

    topology = read(
        "pay-service/src/main/java/com/dongqh/pay/messaging/PayMessaging.java"
    )
    assert 'EVENT_EXCHANGE = "pay.direct"' in topology
    assert 'PAY_SUCCESS_KEY = "pay.success"' in topology
    assert 'DEAD_LETTER_EXCHANGE = "pay.dlx"' in topology

    compose = read("compose.yaml")
    assert "V010__add_pay_message_outbox.sql:/migrations/V010__add_pay_message_outbox.sql:ro" in compose
    assert "< /migrations/V010__add_pay_message_outbox.sql" in compose


def test_trade_and_coupon_services_have_independent_queues_and_dead_letters():
    trade = read(
        "trade-service/src/main/java/com/dongqh/trade/config/PayMessagingConfiguration.java"
    )
    coupon_config = read(
        "coupon-service/src/main/java/com/dongqh/coupon/config/CouponMessagingConfiguration.java"
    )
    coupon_topology = read(
        "coupon-service/src/main/java/com/dongqh/coupon/messaging/CouponMessagingTopology.java"
    )
    assert "trade.pay.success.queue" in trade
    assert "trade.pay.success.dlq" in trade
    assert "CouponMessagingTopology.QUEUE" in coupon_config
    assert "marketing.pay.coupon-use.queue" in coupon_topology
    assert "marketing.pay.coupon-use.dlq" in coupon_topology


def test_pay_success_consumers_use_bounded_retry_then_dead_letter():
    application_paths = (
        "trade-service/src/main/resources/application.yaml",
        "coupon-service/src/main/resources/application.yml",
    )
    for application_path in application_paths:
        application = read(application_path)
        assert "default-requeue-rejected: false" in application
        assert "retry:" in application
        assert "enabled: true" in application
        assert "max-retries: 2" in application
