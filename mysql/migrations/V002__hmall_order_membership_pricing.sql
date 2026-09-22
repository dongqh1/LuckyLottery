-- Adds immutable membership-pricing snapshots to Hmall orders without changing the original dump.
USE `hmall`;

DELIMITER //
CREATE PROCEDURE ensure_hmall_order_membership_pricing()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'hmall' AND table_name = 'order' AND column_name = 'original_fee'
    ) THEN
        ALTER TABLE `order` ADD COLUMN `original_fee` INT NULL COMMENT '商品原价总额，单位为分' AFTER `total_fee`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'hmall' AND table_name = 'order' AND column_name = 'membership_discount_fee'
    ) THEN
        ALTER TABLE `order` ADD COLUMN `membership_discount_fee` INT NOT NULL DEFAULT 0 COMMENT '会员优惠金额，单位为分' AFTER `original_fee`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'hmall' AND table_name = 'order' AND column_name = 'membership_level'
    ) THEN
        ALTER TABLE `order` ADD COLUMN `membership_level` VARCHAR(20) NULL COMMENT '下单时会员等级快照' AFTER `membership_discount_fee`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'hmall' AND table_name = 'order' AND column_name = 'membership_discount_basis_points'
    ) THEN
        ALTER TABLE `order` ADD COLUMN `membership_discount_basis_points` INT NOT NULL DEFAULT 10000 COMMENT '下单时会员折扣基点' AFTER `membership_level`;
    END IF;
END//
DELIMITER ;

CALL ensure_hmall_order_membership_pricing();
DROP PROCEDURE ensure_hmall_order_membership_pricing;

UPDATE `order` SET `original_fee` = `total_fee` WHERE `original_fee` IS NULL;
ALTER TABLE `order` MODIFY COLUMN `original_fee` INT NOT NULL COMMENT '商品原价总额，单位为分';
