-- Provision the isolated trade-service database while preserving current hmall tables.
-- This migration is idempotent because schema-init runs on every stack start.

CREATE DATABASE IF NOT EXISTS `hm_trade`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SET @hm_trade_password = COALESCE(@hm_trade_password, 'hmall-lottery-trade-dev');
SET @create_hm_trade_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'hm_trade_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_trade_password)
);
PREPARE create_hm_trade_stmt FROM @create_hm_trade_sql;
EXECUTE create_hm_trade_stmt;
DEALLOCATE PREPARE create_hm_trade_stmt;

SET @alter_hm_trade_sql = CONCAT(
    "ALTER USER 'hm_trade_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_trade_password)
);
PREPARE alter_hm_trade_stmt FROM @alter_hm_trade_sql;
EXECUTE alter_hm_trade_stmt;
DEALLOCATE PREPARE alter_hm_trade_stmt;

REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'hm_trade_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `hm_trade`.* TO 'hm_trade_app'@'%';

CREATE TABLE IF NOT EXISTS `hm_trade`.`undo_log` (
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

CREATE TABLE IF NOT EXISTS `hm_trade`.`order` LIKE `hmall`.`order`;
CREATE TABLE IF NOT EXISTS `hm_trade`.`order_detail` LIKE `hmall`.`order_detail`;
CREATE TABLE IF NOT EXISTS `hm_trade`.`order_logistics` LIKE `hmall`.`order_logistics`;
INSERT IGNORE INTO `hm_trade`.`order` SELECT * FROM `hmall`.`order`;
INSERT IGNORE INTO `hm_trade`.`order_detail` SELECT * FROM `hmall`.`order_detail`;
INSERT IGNORE INTO `hm_trade`.`order_logistics` SELECT * FROM `hmall`.`order_logistics`;
