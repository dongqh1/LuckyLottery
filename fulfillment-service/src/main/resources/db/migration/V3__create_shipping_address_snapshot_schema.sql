CREATE TABLE shipping_address_snapshot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    snapshot_no VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    address_id BIGINT UNSIGNED NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    receiver_ciphertext TEXT NOT NULL,
    phone_ciphertext TEXT NOT NULL,
    province_ciphertext TEXT NOT NULL,
    city_ciphertext TEXT NOT NULL,
    district_ciphertext TEXT NOT NULL,
    detail_ciphertext TEXT NOT NULL,
    receiver_masked VARCHAR(64) NOT NULL,
    phone_masked VARCHAR(32) NOT NULL,
    region_masked VARCHAR(200) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipping_snapshot_no (snapshot_no),
    UNIQUE KEY uk_shipping_snapshot_source (source_type, source_id),
    KEY idx_shipping_snapshot_user (user_id, id),
    CONSTRAINT chk_shipping_snapshot_source_type CHECK (
        source_type IN ('LOTTERY_BENEFIT', 'CASH_ORDER', 'POINTS_REDEMPTION')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='履约加密地址快照';
