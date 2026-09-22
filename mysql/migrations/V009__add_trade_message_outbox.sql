-- Durable transactional outbox for trade-service domain events.
CREATE TABLE IF NOT EXISTS `hm_trade`.`trade_message_outbox` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `event_id` VARCHAR(36) NOT NULL,
    `event_type` VARCHAR(64) NOT NULL,
    `aggregate_id` VARCHAR(64) NOT NULL,
    `payload` JSON NOT NULL,
    `status` VARCHAR(16) NOT NULL,
    `retry_count` INT NOT NULL DEFAULT 0,
    `last_error` VARCHAR(500) NULL,
    `claim_token` VARCHAR(36) NULL,
    `next_retry_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL,
    `sent_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trade_outbox_event_id` (`event_id`),
    KEY `idx_trade_outbox_relay` (`status`, `next_retry_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='trade-service transactional message outbox';
