-- Provision the isolated item-service database while preserving hmall.item.
-- This migration is idempotent because schema-init runs on every stack start.

CREATE DATABASE IF NOT EXISTS `hm_item`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SET @hm_item_password = COALESCE(@hm_item_password, 'change-item-db-password');
SET @create_hm_item_user_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'hm_item_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_item_password)
);
PREPARE create_hm_item_user_stmt FROM @create_hm_item_user_sql;
EXECUTE create_hm_item_user_stmt;
DEALLOCATE PREPARE create_hm_item_user_stmt;

SET @alter_hm_item_user_sql = CONCAT(
    "ALTER USER 'hm_item_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_item_password)
);
PREPARE alter_hm_item_user_stmt FROM @alter_hm_item_user_sql;
EXECUTE alter_hm_item_user_stmt;
DEALLOCATE PREPARE alter_hm_item_user_stmt;

GRANT SELECT, INSERT, UPDATE, DELETE
     ON `hm_item`.*
     TO 'hm_item_app'@'%';

CREATE TABLE IF NOT EXISTS `hm_item`.`undo_log` (
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

CREATE TABLE IF NOT EXISTS `hm_item`.`item`
    LIKE `hmall`.`item`;

-- Seed only missing ids. Existing rows in hm_item remain owned by item-service.
INSERT IGNORE INTO `hm_item`.`item`
SELECT * FROM `hmall`.`item`;
