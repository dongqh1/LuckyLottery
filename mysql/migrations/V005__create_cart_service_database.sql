-- Provision the isolated cart-service database while preserving hmall.cart.
-- This migration is idempotent because schema-init runs on every stack start.

CREATE DATABASE IF NOT EXISTS `hm_cart`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SET @hm_cart_password = COALESCE(@hm_cart_password, 'change-cart-db-password');
SET @create_hm_cart_user_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'hm_cart_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_cart_password)
);
PREPARE create_hm_cart_user_stmt FROM @create_hm_cart_user_sql;
EXECUTE create_hm_cart_user_stmt;
DEALLOCATE PREPARE create_hm_cart_user_stmt;

SET @alter_hm_cart_user_sql = CONCAT(
    "ALTER USER 'hm_cart_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_cart_password)
);
PREPARE alter_hm_cart_user_stmt FROM @alter_hm_cart_user_sql;
EXECUTE alter_hm_cart_user_stmt;
DEALLOCATE PREPARE alter_hm_cart_user_stmt;

GRANT SELECT, INSERT, UPDATE, DELETE
     ON `hm_cart`.*
     TO 'hm_cart_app'@'%';

CREATE TABLE IF NOT EXISTS `hm_cart`.`undo_log` (
    `branch_id` BIGINT NOT NULL,
    `xid` VARCHAR(128) NOT NULL,
    `context` VARCHAR(128) NOT NULL,
    `rollback_info` LONGBLOB NOT NULL,
    `log_status` INT NOT NULL,
    `log_created` DATETIME(6) NOT NULL,
    `log_modified` DATETIME(6) NOT NULL,
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`),
    KEY `ix_log_created` (`log_created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT transaction mode undo table';

CREATE TABLE IF NOT EXISTS `hm_cart`.`cart`
    LIKE `hmall`.`cart`;

-- Seed only missing ids. Existing rows in hm_cart remain owned by cart-service.
INSERT IGNORE INTO `hm_cart`.`cart`
SELECT * FROM `hmall`.`cart`;
