CREATE TABLE points_redemption_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    redemption_no VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    sku_id BIGINT UNSIGNED NULL,
    offer_id BIGINT UNSIGNED NULL,
    hmall_item_id BIGINT UNSIGNED NULL,
    quantity INT UNSIGNED NOT NULL,
    unit_points BIGINT UNSIGNED NOT NULL,
    total_points BIGINT UNSIGNED NOT NULL,
    cash_price_cent INT UNSIGNED NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sku_name VARCHAR(100) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    image_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    process_step VARCHAR(40) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    address_id BIGINT UNSIGNED NULL,
    shipping_order_id BIGINT UNSIGNED NULL,
    reversal_no VARCHAR(64) NULL,
    failure_reason VARCHAR(500) NULL,
    original_error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_redemption_no (redemption_no),
    UNIQUE KEY uk_points_redemption_reversal (reversal_no),
    KEY idx_points_redemption_user_created (user_id, created_at, id),
    KEY idx_points_redemption_shipping_order (shipping_order_id),
    CONSTRAINT chk_points_redemption_quantity CHECK (quantity > 0),
    CONSTRAINT chk_points_redemption_price CHECK (unit_points > 0 AND total_points > 0),
    CONSTRAINT chk_points_redemption_type CHECK (product_type IN ('PHYSICAL', 'VIRTUAL')),
    CONSTRAINT chk_points_redemption_status CHECK (
        status IN ('PROCESSING', 'COMPLETED', 'COMPENSATED', 'REVERSED')
    ),
    CONSTRAINT chk_points_redemption_source CHECK (
        (sku_id IS NOT NULL AND offer_id IS NULL AND hmall_item_id IS NULL)
        OR (sku_id IS NULL AND offer_id IS NOT NULL AND hmall_item_id IS NOT NULL)
    ),
    CONSTRAINT chk_points_redemption_address CHECK (
        (product_type = 'PHYSICAL' AND address_id IS NOT NULL)
        OR (product_type = 'VIRTUAL' AND address_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分兑换单';
