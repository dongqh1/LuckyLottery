CREATE TABLE points_redemption_offer (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    hmall_item_id BIGINT UNSIGNED NOT NULL,
    unit_points BIGINT NOT NULL,
    available_stock INT UNSIGNED NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    image_url VARCHAR(500) NULL,
    cash_price_cent INT UNSIGNED NOT NULL,
    status TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_offer_hmall_item (hmall_item_id),
    KEY idx_points_offer_status_stock (status, available_stock, id),
    CONSTRAINT chk_points_offer_points CHECK (unit_points > 0),
    CONSTRAINT chk_points_offer_stock CHECK (available_stock <= 1000000),
    CONSTRAINT chk_points_offer_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hmall商品积分兑换报价';

ALTER TABLE points_redemption_order
    MODIFY COLUMN sku_id BIGINT UNSIGNED NULL,
    ADD COLUMN offer_id BIGINT UNSIGNED NULL AFTER sku_id,
    ADD COLUMN hmall_item_id BIGINT UNSIGNED NULL AFTER offer_id,
    ADD COLUMN cash_price_cent INT UNSIGNED NULL AFTER total_points,
    ADD KEY idx_points_redemption_offer (offer_id),
    ADD KEY idx_points_redemption_hmall_item (hmall_item_id),
    ADD CONSTRAINT chk_points_redemption_source CHECK (
        (sku_id IS NOT NULL AND offer_id IS NULL AND hmall_item_id IS NULL)
        OR (sku_id IS NULL AND offer_id IS NOT NULL AND hmall_item_id IS NOT NULL)
    );