import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GATEWAY_CONFIG = ROOT / "hm-gateway/src/main/resources/application.yaml"


def route_block(application, route_id):
    match = re.search(
        rf"(?ms)^\s+- id: {re.escape(route_id)}\s+.*?(?=^\s+- id:|\Z)",
        application,
    )
    return match.group(0) if match else None


class ExtractedMarketingGatewayRoutesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.application = GATEWAY_CONFIG.read_text(encoding="utf-8")

    def assert_routes_before_legacy_fallback(self, route_id, service_uri, path):
        block = route_block(self.application, route_id)
        self.assertIsNotNone(block, f"missing gateway route {route_id}")
        self.assertIn(f"uri: {service_uri}", block)
        self.assertIn(f"- Path={path}", block)
        self.assertLess(
            self.application.index(f"id: {route_id}"),
            self.application.index("id: marketing-legacy"),
        )

    def test_points_api_routes_to_points_service_before_legacy_fallback(self):
        self.assert_routes_before_legacy_fallback(
            "points-service",
            "lb://points-service",
            "/api/points/**,/api/admin/points/**",
        )

    def test_coupon_api_routes_to_coupon_service_before_legacy_fallback(self):
        self.assert_routes_before_legacy_fallback(
            "coupon-service",
            "lb://coupon-service",
            "/api/coupons/**,/api/admin/coupon-templates/**,/api/admin/coupons/**",
        )

    def test_membership_api_routes_to_membership_service_before_legacy_fallback(self):
        self.assert_routes_before_legacy_fallback(
            "membership-service",
            "lb://membership-service",
            "/api/memberships/**,/api/admin/membership-products/**",
        )

    def test_lottery_and_benefit_apis_route_to_lottery_service_before_legacy_fallback(self):
        self.assert_routes_before_legacy_fallback(
            "lottery-service",
            "lb://lottery-service",
            "/api/lottery/**,/api/benefits/**,/api/admin/activities/**,/api/admin/prizes/**,"
            "/api/admin/prize-images/**,/api/admin/reward-definitions/**",
        )


if __name__ == "__main__":
    unittest.main()
