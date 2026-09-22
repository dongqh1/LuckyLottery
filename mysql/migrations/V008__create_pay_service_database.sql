-- Provision the isolated pay-service database while preserving hmall.pay_order.
-- This migration is idempotent because schema-init runs on every stack start.

CREATE DATABASE IF NOT EXISTS `hm_pay`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SET @hm_pay_password = COALESCE(@hm_pay_password, 'hmall-lottery-pay-dev');
SET @create_hm_pay_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'hm_pay_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_pay_password)
);
PREPARE create_hm_pay_stmt FROM @create_hm_pay_sql;
EXECUTE create_hm_pay_stmt;
DEALLOCATE PREPARE create_hm_pay_stmt;

SET @alter_hm_pay_sql = CONCAT(
    "ALTER USER 'hm_pay_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_pay_password)
);
PREPARE alter_hm_pay_stmt FROM @alter_hm_pay_sql;
EXECUTE alter_hm_pay_stmt;
DEALLOCATE PREPARE alter_hm_pay_stmt;

REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'hm_pay_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `hm_pay`.* TO 'hm_pay_app'@'%';

CREATE TABLE IF NOT EXISTS `hm_pay`.`undo_log` (
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

CREATE TABLE IF NOT EXISTS `hm_pay`.`pay_order` LIKE `hmall`.`pay_order`;
INSERT IGNORE INTO `hm_pay`.`pay_order` SELECT * FROM `hmall`.`pay_order`;
