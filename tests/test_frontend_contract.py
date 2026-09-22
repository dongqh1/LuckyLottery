from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
PORTAL = ROOT / "nginx" / "html" / "hmall-portal"


def read(name: str) -> str:
    return (PORTAL / name).read_text(encoding="utf-8")


class AddressPageContractTest(unittest.TestCase):
    def test_address_page_uses_authenticated_hmall_crud(self):
        page = read("address.html")
        self.assertIn("js/common.js", page)
        self.assertIn('axios.get("/addresses")', page)
        self.assertIn('axios.post("/addresses"', page)
        self.assertRegex(page, r'axios\.put\(["`]\/addresses\/\$\{')
        self.assertRegex(page, r'axios\.delete\(["`]\/addresses\/\$\{')
        self.assertNotIn("userId", page)

    def test_address_page_has_real_operational_copy(self):
        page = read("address.html")
        self.assertIn("收货地址", page)
        self.assertIn("设为默认地址", page)
        self.assertNotRegex(page, re.compile(r"测试|E2E", re.IGNORECASE))


class OrderConfirmationContractTest(unittest.TestCase):
    def test_order_confirmation_requires_a_real_selected_address(self):
        page = read("order-confirm.html")
        self.assertIn("addressId: null", page)
        self.assertIn('axios.get("/addresses")', page)
        self.assertIn("if(!this.params.addressId)", page)
        self.assertNotIn("addressId: 61", page)
        self.assertNotIn("朝阳区三环到四环之间", page)

    def test_order_confirmation_displays_real_membership_discount_quote(self):
        page = read("order-confirm.html")
        self.assertIn('/marketing/memberships/me', page)
        self.assertIn("商品原价", page)
        self.assertIn("GOLD 会员优惠", page)
        self.assertIn("membershipDiscount", page)
        self.assertIn("payablePrice", page)

    def test_order_confirmation_lists_and_submits_applicable_coupon(self):
        page = read("order-confirm.html")
        self.assertIn("/marketing/coupons/checkout/applicable", page)
        self.assertIn("selectedCouponId", page)
        self.assertIn("coupon.userCouponId", page)
        self.assertIn("couponDiscount", page)
        self.assertIn("couponId: this.selectedCouponId", page)


class StorefrontOrdersContractTest(unittest.TestCase):
    def test_shared_storefront_header_exposes_my_orders_and_coupons(self):
        header = read("js/top.js")
        self.assertIn('/my-orders.html', header)
        self.assertIn('/coupons.html', header)
        page = read('my-orders.html')
        self.assertIn('/orders/me', page)
        self.assertIn('order-detail.html?orderId=', page)


class PaymentCouponContractTest(unittest.TestCase):
    def test_payment_page_shows_coupon_snapshot_and_can_release_reserved_coupon(self):
        page = read("pay.html")
        self.assertIn("couponDiscountFee", page)
        self.assertIn("couponName", page)
        self.assertIn('axios.delete("/orders/" + this.order.id)', page)
        self.assertIn("取消订单并释放优惠券", page)


class PointsMallContractTest(unittest.TestCase):
    def test_points_page_redeems_hmall_offer_with_real_address_and_fulfillment(self):
        page = read("points.html")
        self.assertIn('axios.get("/marketing/points/offers")', page)
        self.assertIn('axios.get("/marketing/points/account")', page)
        self.assertIn('axios.get("/addresses")', page)
        self.assertIn('axios.post("/marketing/points/redemptions"', page)
        self.assertIn("offerId: offer.id", page)
        self.assertIn("addressId: this.selectedAddressId", page)
        self.assertIn('axios.get("/marketing/points/redemptions"', page)
        self.assertIn("addressSnapshot", page)
        self.assertIn("shippingNo", page)
        self.assertNotRegex(page, re.compile(r"测试|E2E", re.IGNORECASE))


class CouponCenterContractTest(unittest.TestCase):
    def test_coupon_center_groups_lifecycle_and_shows_usage(self):
        page = read("coupons.html")
        self.assertIn("axios.get('/marketing/coupons'", page)
        self.assertNotIn("/marketing/coupons/mine", page)
        self.assertIn("AVAILABLE", page)
        self.assertIn("LOCKED", page)
        self.assertIn("USED", page)
        self.assertIn("EXPIRED", page)
        self.assertIn("templateName", page)
        self.assertIn("discountCent", page)
        self.assertIn("usedOrderNo", page)
        self.assertIn("BRAND", page)
        self.assertIn("hmallBrand", page)

    def test_marketing_navigation_links_coupon_center(self):
        source = read("js/marketing-header.js")
        self.assertIn("/coupons.html", source)
        self.assertIn("我的优惠券", source)


class BenefitCategoryContractTest(unittest.TestCase):
    def test_benefit_page_groups_assets_into_four_operational_categories(self):
        page = read("benefits.html")
        self.assertIn("activeCategory", page)
        self.assertIn("visibleBenefits", page)
        self.assertIn("categoryOf(item)", page)
        self.assertIn("item.rewardType||item.prizeType", page)
        for value, label in (
            ("PHYSICAL", "实物"),
            ("POINTS", "积分"),
            ("COUPON", "优惠券"),
            ("MEMBERSHIP", "会员权益"),
        ):
            self.assertIn(f"value:'{value}',label:'{label}'", page)

    def test_draw_chance_remains_visible_as_account_reward(self):
        page = read("benefits.html")
        self.assertIn("DRAW_CHANCE", page)
        self.assertIn("return 'POINTS'", page)
        self.assertIn("抽奖次数", page)


class OrderShippingDetailContractTest(unittest.TestCase):
    def test_payment_success_links_to_real_order_detail(self):
        page = read("paysuccess.html")
        self.assertIn("/order-detail.html?orderId=", page)
        self.assertIn("order.id", page)

    def test_order_detail_shows_hmall_delivery_snapshot_and_real_progress_times(self):
        detail = PORTAL / "order-detail.html"
        self.assertTrue(detail.exists(), "订单详情页尚未创建")
        page = detail.read_text(encoding="utf-8")
        self.assertIn('axios.get("/orders/"', page)
        self.assertIn("delivery.carrier", page)
        self.assertIn("delivery.trackingNumber", page)
        self.assertIn("delivery.addressSnapshot.fullAddress", page)
        self.assertIn("progressSteps", page)
        for field in ("createTime", "payTime", "consignTime", "endTime"):
            self.assertIn(field, page)


if __name__ == "__main__":
    unittest.main()
