-- Adds immutable coupon-pricing snapshots to Hmall orders.
USE `hmall`;

DELIMITER //
CREATE PROCEDURE ensure_hmall_order_coupon_pricing()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'hmall' AND table_name = 'order' AND column_name = 'coupon_id'
    ) THEN
        ALTER TABLE `order` ADD COLUMN `coupon_id` BIGINT NULL COMMENT 'locked LuckyHub user_coupon.id' AFTER `membership_discount_basis_points`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'hmall' AND table_name = 'order' AND column_name = 'coupon_template_id'
    ) THEN
        ALTER TABLE `order` ADD COLUMN `coupon_template_id` BIGINT NULL COMMENT 'coupon template snapshot' AFTER `coupon_id`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'hmall' AND table_name = 'order' AND column_name = 'coupon_name'
    ) THEN
        ALTER TABLE `order` ADD COLUMN `coupon_name` VARCHAR(100) NULL COMMENT 'coupon name snapshot' AFTER `coupon_template_id`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'hmall' AND table_name = 'order' AND column_name = 'coupon_discount_fee'
    ) THEN
        ALTER TABLE `order` ADD COLUMN `coupon_discount_fee` INT NOT NULL DEFAULT 0 COMMENT 'coupon discount in cents' AFTER `coupon_name`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
         WHERE table_schema = 'hmall' AND table_name = 'order' AND index_name = 'idx_order_coupon_id'
    ) THEN
        ALTER TABLE `order` ADD KEY `idx_order_coupon_id` (`coupon_id`);
    END IF;
END//
DELIMITER ;

CALL ensure_hmall_order_coupon_pricing();
DROP PROCEDURE ensure_hmall_order_coupon_pricing;
