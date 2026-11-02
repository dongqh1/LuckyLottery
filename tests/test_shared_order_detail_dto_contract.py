from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class SharedOrderDetailDtoContractTest(unittest.TestCase):
    def read(self, relative_path: str) -> str:
        return (ROOT / relative_path).read_text(encoding="utf-8")

    def test_hm_api_is_the_only_order_detail_dto_in_split_services(self):
        self.assertTrue(
            (ROOT / "hm-api/src/main/java/com/dongqh/hmapi/dto/OrderDetailDTO.java").is_file()
        )
        self.assertFalse(
            (ROOT / "trade-service/src/main/java/com/dongqh/trade/domain/dto/OrderDetailDTO.java").exists()
        )
        self.assertFalse(
            (ROOT / "item-service/src/main/java/com/dongqh/item/domain/dto/OrderDetailDTO.java").exists()
        )

    def test_shared_dto_has_no_swagger_dependency(self):
        dto = self.read("hm-api/src/main/java/com/dongqh/hmapi/dto/OrderDetailDTO.java")
        self.assertNotIn("io.swagger", dto)
        self.assertIn("private Long itemId;", dto)
        self.assertIn("private Integer num;", dto)

    def test_item_and_trade_consumers_import_the_shared_type(self):
        consumers = (
            "trade-service/src/main/java/com/dongqh/trade/domain/dto/OrderFormDTO.java",
            "trade-service/src/main/java/com/dongqh/trade/controller/OrderController.java",
            "trade-service/src/main/java/com/dongqh/trade/service/IOrderService.java",
            "trade-service/src/main/java/com/dongqh/trade/service/impl/OrderServiceImpl.java",
            "trade-service/src/main/java/com/dongqh/trade/marketing/CouponCheckoutClient.java",
            "trade-service/src/main/java/com/dongqh/trade/marketing/HttpCouponCheckoutClient.java",
            "item-service/src/main/java/com/dongqh/item/controller/ItemController.java",
            "item-service/src/main/java/com/dongqh/item/service/IItemService.java",
            "item-service/src/main/java/com/dongqh/item/service/impl/ItemServiceImpl.java",
            "item-service/src/main/java/com/dongqh/item/mapper/ItemMapper.java",
        )
        shared_import = "import com.dongqh.hmapi.dto.OrderDetailDTO;"
        for relative_path in consumers:
            with self.subTest(path=relative_path):
                self.assertIn(shared_import, self.read(relative_path))

    def test_item_service_declares_hm_api_dependency(self):
        pom = self.read("item-service/pom.xml")
        self.assertIn("<artifactId>hm-api</artifactId>", pom)

    def test_feign_path_and_order_service_residuals_are_fixed(self):
        client = self.read("hm-api/src/main/java/com/dongqh/hmapi/client/ItemClient.java")
        self.assertIn('@PutMapping("/items/stock/deduct")', client)

        service = self.read(
            "trade-service/src/main/java/com/dongqh/trade/service/impl/OrderServiceImpl.java"
        )
        self.assertNotIn("itemService.queryItemByIds(ids)", service)
        self.assertIn("itemClient.queryItemByIds(ids)", service)
        self.assertIn(
            "private OrderLogistics buildLogistics(Long orderId, AddressDTO address)",
            service,
        )

    def test_order_service_imports_logistics_entity_used_by_builder(self):
        service = self.read(
            "trade-service/src/main/java/com/dongqh/trade/service/impl/OrderServiceImpl.java"
        )
        self.assertIn(
            "import com.dongqh.trade.domain.po.OrderLogistics;",
            service,
        )


if __name__ == "__main__":
    unittest.main()
