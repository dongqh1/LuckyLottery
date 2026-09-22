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
        "pay-service/src/main/java/com/dongqh/payservice/service/impl/PayOrderServiceImpl.java"
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
        "pay-service/src/main/java/com/dongqh/payservice/messaging/PayMessaging.java"
    )
    assert 'EVENT_EXCHANGE = "pay.direct"' in topology
    assert 'PAY_SUCCESS_KEY = "pay.success"' in topology
    assert 'DEAD_LETTER_EXCHANGE = "pay.dlx"' in topology

    compose = read("compose.yaml")
    assert "V010__add_pay_message_outbox.sql:/migrations/V010__add_pay_message_outbox.sql:ro" in compose
    assert "< /migrations/V010__add_pay_message_outbox.sql" in compose


def test_trade_and_marketing_have_independent_queues_and_dead_letters():
    trade = read(
        "trade-service/src/main/java/com/dongqh/tradeservice/config/PayMessagingConfiguration.java"
    )
    marketing = read(
        "marketing-service/src/main/java/com/dongqh/luckyhub/coupon/config/PayCouponMessagingConfiguration.java"
    )
    assert "trade.pay.success.queue" in trade
    assert "trade.pay.success.dlq" in trade
    assert "marketing.pay.coupon-use.queue" in marketing
    assert "marketing.pay.coupon-use.dlq" in marketing


def test_pay_success_consumers_use_bounded_retry_then_dead_letter():
    for service in ("trade-service", "marketing-service"):
        application = read(f"{service}/src/main/resources/application.yaml")
        assert "default-requeue-rejected: false" in application
        assert "retry:" in application
        assert "enabled: true" in application
        assert "max-retries: 2" in application
