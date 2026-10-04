CREATE TABLE sim_logistics_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    fulfillment_no VARCHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    request_payload JSON NOT NULL,
    external_reference VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sim_logistics_fulfillment (fulfillment_no),
    UNIQUE KEY uk_sim_logistics_reference (external_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='履约服务物流模拟记录';
