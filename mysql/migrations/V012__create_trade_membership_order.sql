-- Membership purchases are owned by Trade; this table stores the immutable paid-terms snapshot.
CREATE TABLE IF NOT EXISTS `hm_trade`.`trade_membership_order` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `request_key` VARCHAR(64) NOT NULL,
    `membership_product_id` BIGINT NOT NULL,
    `product_code` VARCHAR(64) NOT NULL,
    `product_name` VARCHAR(128) NOT NULL,
    `membership_level` VARCHAR(32) NOT NULL,
    `duration_days` INT NOT NULL,
    `total_fee` INT NOT NULL,
    `discount_basis_points` INT NOT NULL,
    `daily_draw_bonus` INT NOT NULL,
    `points_multiplier_basis_points` INT NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1 unpaid, 2 paid, 5 closed',
    `paid_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trade_membership_user_request` (`user_id`, `request_key`),
    KEY `idx_trade_membership_user_created` (`user_id`, `created_at`, `id`),
    KEY `idx_trade_membership_status_created` (`status`, `created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Trade-owned membership purchase and immutable price/term snapshot';
