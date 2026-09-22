-- Incremental bridge between the original hmall schema and luckyhub marketing.
-- This script is intentionally idempotent because schema-init runs it on every stack start.

CREATE DATABASE IF NOT EXISTS `luckyhub`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- schema-init supplies @luckyhub_password. The fallback keeps the SQL directly runnable.
SET @luckyhub_password = COALESCE(@luckyhub_password, 'luckyhub');
SET @create_luckyhub_user_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'luckyhub'@'%' IDENTIFIED BY ",
    QUOTE(@luckyhub_password)
);
PREPARE create_luckyhub_user_stmt FROM @create_luckyhub_user_sql;
EXECUTE create_luckyhub_user_stmt;
DEALLOCATE PREPARE create_luckyhub_user_stmt;

SET @alter_luckyhub_user_sql = CONCAT(
    "ALTER USER 'luckyhub'@'%' IDENTIFIED BY ",
    QUOTE(@luckyhub_password)
);
PREPARE alter_luckyhub_user_stmt FROM @alter_luckyhub_user_sql;
EXECUTE alter_luckyhub_user_stmt;
DEALLOCATE PREPARE alter_luckyhub_user_stmt;

GRANT ALL PRIVILEGES ON `luckyhub`.* TO 'luckyhub'@'%';

USE `hmall`;

DELIMITER //
CREATE PROCEDURE ensure_hmall_user_role()
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'hmall'
           AND table_name = 'user'
           AND column_name = 'role'
    ) THEN
        ALTER TABLE `user`
            ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'USER';
    END IF;
END//
DELIMITER ;

CALL ensure_hmall_user_role();
DROP PROCEDURE ensure_hmall_user_role;

UPDATE `user`
   SET `role` = 'ADMIN'
 WHERE `id` = 1;

CREATE TABLE IF NOT EXISTS `order_reward_outbox` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `event_id` VARCHAR(64) NOT NULL,
    `order_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `event_type` VARCHAR(50) NOT NULL DEFAULT 'ORDER_COMPLETED',
    `payload` JSON NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `retry_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `next_retry_at` DATETIME(3) NULL,
    `delivered_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_reward_outbox_event` (`event_id`),
    KEY `idx_order_reward_outbox_delivery` (`status`, `next_retry_at`, `id`),
    KEY `idx_order_reward_outbox_order` (`order_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='商城订单奖励事件发件箱';
