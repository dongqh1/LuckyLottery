CREATE TABLE points_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_account_user (user_id),
    CONSTRAINT chk_points_account_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户积分账户';

CREATE TABLE points_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reversal_of_ledger_id BIGINT UNSIGNED NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_ledger_business (business_type, business_id),
    UNIQUE KEY uk_points_ledger_reversal (reversal_of_ledger_id),
    KEY idx_points_ledger_user_created (user_id, created_at, id),
    CONSTRAINT chk_points_ledger_business_type CHECK (
        business_type IN ('LOTTERY_REWARD', 'ORDER_REWARD', 'MEMBERSHIP_BONUS',
                          'REDEMPTION', 'REVERSAL', 'MANUAL_ADJUSTMENT')
    ),
    CONSTRAINT chk_points_ledger_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_points_ledger_amount CHECK (amount > 0),
    CONSTRAINT chk_points_ledger_balance CHECK (balance_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变积分流水';

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
