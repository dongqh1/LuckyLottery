CREATE TABLE shipping_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shipping_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    target_user_id BIGINT UNSIGNED NOT NULL,
    address_snapshot_id BIGINT UNSIGNED NOT NULL,
    hmall_item_id BIGINT UNSIGNED NULL,
    sku_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    image_url VARCHAR(500) NULL,
    quantity INT UNSIGNED NOT NULL,
    fulfillment_no VARCHAR(64) NULL,
    claim_request_id VARCHAR(64) NULL,
    carrier_code VARCHAR(32) NULL,
    carrier_name VARCHAR(64) NULL,
    waybill_no VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    shipped_at DATETIME(3) NULL,
    delivered_at DATETIME(3) NULL,
    failed_at DATETIME(3) NULL,
    terminated_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipping_order_no (shipping_no),
    UNIQUE KEY uk_shipping_order_source (source_type, source_id),
    UNIQUE KEY uk_shipping_order_fulfillment (fulfillment_no),
    UNIQUE KEY uk_shipping_order_claim_request (claim_request_id),
    UNIQUE KEY uk_shipping_order_waybill (waybill_no),
    KEY idx_shipping_order_user_created (target_user_id, created_at, id),
    KEY idx_shipping_order_status_updated (status, updated_at, id),
    CONSTRAINT chk_shipping_order_source_type CHECK (
        source_type IN ('LOTTERY_BENEFIT', 'CASH_ORDER', 'POINTS_REDEMPTION')
    ),
    CONSTRAINT chk_shipping_order_status CHECK (
        status IN ('READY', 'FULFILLING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'FAILED', 'TERMINATED')
    ),
    CONSTRAINT chk_shipping_order_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='履约服务实物发货单';

CREATE TABLE shipping_tracking_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shipping_order_id BIGINT UNSIGNED NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL,
    waybill_no VARCHAR(100) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    location_summary VARCHAR(200) NULL,
    description VARCHAR(500) NULL,
    event_time DATETIME(3) NOT NULL,
    received_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipping_tracking_provider_event (provider_event_id),
    KEY idx_shipping_tracking_order_event (shipping_order_id, event_time, id),
    KEY idx_shipping_tracking_waybill_event (waybill_no, event_time, id),
    CONSTRAINT chk_shipping_tracking_event_type CHECK (
        event_type IN ('PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='履约服务物流轨迹';

CREATE TABLE shipping_callback_receipt (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    callback_id VARCHAR(100) NOT NULL,
    nonce_digest CHAR(64) NOT NULL,
    signature_digest CHAR(64) NOT NULL,
    waybill_no VARCHAR(100) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    event_time DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    received_at DATETIME(3) NOT NULL,
    processed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipping_callback_id (callback_id),
    UNIQUE KEY uk_shipping_callback_nonce_digest (nonce_digest),
    KEY idx_shipping_callback_waybill_received (waybill_no, received_at, id),
    CONSTRAINT chk_shipping_callback_event_type CHECK (
        event_type IN ('PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED')
    ),
    CONSTRAINT chk_shipping_callback_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='履约服务物流回调安全收据';
