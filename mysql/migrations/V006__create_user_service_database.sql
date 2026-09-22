-- Provision the isolated user-service database while preserving current hmall tables.
-- This migration is idempotent because schema-init runs on every stack start.

CREATE DATABASE IF NOT EXISTS `hm_user`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SET @hm_user_password = COALESCE(@hm_user_password, 'hmall-lottery-user-dev');
SET @create_hm_user_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'hm_user_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_user_password)
);
PREPARE create_hm_user_stmt FROM @create_hm_user_sql;
EXECUTE create_hm_user_stmt;
DEALLOCATE PREPARE create_hm_user_stmt;

SET @alter_hm_user_sql = CONCAT(
    "ALTER USER 'hm_user_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_user_password)
);
PREPARE alter_hm_user_stmt FROM @alter_hm_user_sql;
EXECUTE alter_hm_user_stmt;
DEALLOCATE PREPARE alter_hm_user_stmt;

REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'hm_user_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `hm_user`.* TO 'hm_user_app'@'%';

CREATE TABLE IF NOT EXISTS `hm_user`.`undo_log` (
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

CREATE TABLE IF NOT EXISTS `hm_user`.`user` LIKE `hmall`.`user`;
CREATE TABLE IF NOT EXISTS `hm_user`.`address` LIKE `hmall`.`address`;
INSERT IGNORE INTO `hm_user`.`user` SELECT * FROM `hmall`.`user`;
INSERT IGNORE INTO `hm_user`.`address` SELECT * FROM `hmall`.`address`;
