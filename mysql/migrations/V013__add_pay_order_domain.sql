-- A side table keeps the existing Trade pay_order schema and queries unchanged.
CREATE TABLE IF NOT EXISTS `hm_pay`.`pay_order_domain` (
    `pay_order_id` BIGINT NOT NULL,
    `biz_order_no` BIGINT NOT NULL,
    `order_domain` VARCHAR(16) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`pay_order_id`),
    UNIQUE KEY `uk_pay_domain_business_order` (`order_domain`, `biz_order_no`),
    KEY `idx_pay_domain_business_order` (`biz_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Pay-service business order domain extension';
