CREATE TABLE product (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    image_url VARCHAR(500) NULL,
    description VARCHAR(1000) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_code (product_code),
    KEY idx_product_status_created (status, created_at),
    CONSTRAINT chk_product_type CHECK (product_type IN ('PHYSICAL', 'VIRTUAL')),
    CONSTRAINT chk_product_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Marketing商品目录';

CREATE TABLE product_sku (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sku_name VARCHAR(100) NOT NULL,
    cash_price_cent BIGINT UNSIGNED NULL,
    points_price BIGINT UNSIGNED NULL,
    cash_enabled TINYINT NOT NULL DEFAULT 0,
    points_enabled TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku_code (sku_code),
    KEY idx_product_sku_product (product_id, status),
    CONSTRAINT chk_product_sku_cash CHECK (
        (cash_enabled = 0) OR (cash_enabled = 1 AND cash_price_cent IS NOT NULL)
    ),
    CONSTRAINT chk_product_sku_points CHECK (
        (points_enabled = 0) OR (points_enabled = 1 AND points_price IS NOT NULL)
    ),
    CONSTRAINT chk_product_sku_enabled CHECK (cash_enabled IN (0, 1) AND points_enabled IN (0, 1)),
    CONSTRAINT chk_product_sku_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Marketing商品SKU';

CREATE TABLE sku_inventory (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_id BIGINT UNSIGNED NOT NULL,
    total_stock INT UNSIGNED NOT NULL,
    allocated_stock INT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_inventory_sku (sku_id),
    CONSTRAINT chk_sku_inventory_allocation CHECK (allocated_stock <= total_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Marketing SKU总库存';

CREATE TABLE inventory_channel_stock (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_id BIGINT UNSIGNED NOT NULL,
    channel_code VARCHAR(100) NOT NULL,
    allocated_stock INT UNSIGNED NOT NULL,
    available_stock INT UNSIGNED NOT NULL,
    reserved_stock INT UNSIGNED NOT NULL DEFAULT 0,
    consumed_stock INT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_stock_sku_channel (sku_id, channel_code),
    KEY idx_channel_stock_channel (channel_code),
    CONSTRAINT chk_channel_stock_balance CHECK (
        allocated_stock = available_stock + reserved_stock + consumed_stock
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Marketing SKU渠道库存';

CREATE TABLE inventory_reservation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reservation_no VARCHAR(64) NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    channel_code VARCHAR(100) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_reservation_no (reservation_no),
    KEY idx_inventory_reservation_sku_channel (sku_id, channel_code),
    CONSTRAINT chk_inventory_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_reservation_status CHECK (
        status IN ('RESERVED', 'CONFIRMED', 'RELEASED', 'REVERSED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Marketing SKU渠道库存预占';

CREATE TABLE inventory_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_no VARCHAR(100) NOT NULL,
    sku_id BIGINT UNSIGNED NULL,
    hmall_item_id BIGINT UNSIGNED NULL,
    channel_code VARCHAR(100) NULL,
    operation VARCHAR(20) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_ledger_business_no (business_no),
    KEY idx_inventory_ledger_sku_created (sku_id, created_at),
    KEY idx_inventory_ledger_hmall_item_created (hmall_item_id, created_at),
    CONSTRAINT chk_inventory_ledger_identity CHECK (
        (sku_id IS NOT NULL AND hmall_item_id IS NULL)
        OR (sku_id IS NULL AND hmall_item_id IS NOT NULL)
    ),
    CONSTRAINT chk_inventory_ledger_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_ledger_operation CHECK (
        operation IN ('INITIALIZE', 'ALLOCATE', 'RESERVE', 'CONFIRM', 'RELEASE', 'RETURN', 'CLAIM_RETURN')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存不可变流水';
