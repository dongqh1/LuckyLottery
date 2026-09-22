CREATE TABLE fulfillment_outbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    fulfillment_no VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    next_retry_at DATETIME(3) NULL,
    claim_token VARCHAR(64) NULL,
    claim_until DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    sent_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fulfillment_outbox_event_id (event_id),
    KEY idx_fulfillment_outbox_relay (status, next_retry_at, claim_until, id),
    KEY idx_fulfillment_outbox_task (fulfillment_no, id),
    CONSTRAINT chk_fulfillment_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='履约任务可靠触发发件箱';
