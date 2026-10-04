CREATE TABLE reward_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reward_code VARCHAR(64) NOT NULL,
    reward_name VARCHAR(100) NOT NULL,
    reward_type VARCHAR(30) NOT NULL,
    target_id BIGINT UNSIGNED NULL,
    quantity BIGINT UNSIGNED NOT NULL,
    config_snapshot JSON NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reward_definition_code (reward_code),
    KEY idx_reward_definition_type_status (reward_type, status),
    CONSTRAINT chk_reward_definition_type CHECK (
        reward_type IN ('PRODUCT', 'COUPON', 'POINTS', 'MEMBERSHIP', 'DRAW_CHANCE')
    ),
    CONSTRAINT chk_reward_definition_quantity CHECK (quantity > 0),
    CONSTRAINT chk_reward_definition_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一奖励定义';

CREATE TABLE marketing_prize (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    prize_name VARCHAR(100) NOT NULL COMMENT '奖品名称',
    prize_type VARCHAR(30) NOT NULL COMMENT '奖品类型',
    reward_definition_id BIGINT UNSIGNED NULL,
    prize_level VARCHAR(30) NOT NULL COMMENT '奖品等级',
    image_url VARCHAR(500) NULL COMMENT '图片地址',
    description VARCHAR(500) NULL COMMENT '奖品说明',
    stackable TINYINT NOT NULL DEFAULT 0 COMMENT '是否可叠加：0-否，1-是',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_marketing_prize_type (prize_type),
    KEY idx_marketing_prize_status (status),
    KEY idx_marketing_prize_reward_definition (reward_definition_id),
    CONSTRAINT chk_marketing_prize_stackable CHECK (stackable IN (0, 1)),
    CONSTRAINT chk_marketing_prize_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖品表';

CREATE TABLE marketing_activity (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_name VARCHAR(100) NOT NULL COMMENT '活动名称',
    description VARCHAR(1000) NULL COMMENT '活动说明',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT '活动状态',
    start_time DATETIME(3) NOT NULL COMMENT '开始时间',
    end_time DATETIME(3) NOT NULL COMMENT '结束时间',
    daily_limit INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '用户每日参与次数上限',
    no_win_weight INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '独立未中奖权重',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_marketing_activity_status (status),
    KEY idx_marketing_activity_start_time (start_time),
    KEY idx_marketing_activity_end_time (end_time),
    KEY idx_marketing_activity_name (activity_name),
    KEY idx_marketing_activity_created_by (created_by),
    CONSTRAINT chk_marketing_activity_time CHECK (end_time > start_time),
    CONSTRAINT chk_marketing_activity_daily_limit CHECK (daily_limit > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='营销活动表';

CREATE TABLE marketing_activity_prize (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    prize_id BIGINT UNSIGNED NOT NULL COMMENT '奖品ID',
    weight INT UNSIGNED NOT NULL COMMENT '中奖权重',
    total_stock INT UNSIGNED NOT NULL COMMENT '总库存',
    remaining_stock INT UNSIGNED NOT NULL COMMENT '数据库剩余库存',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '展示顺序',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_prize (activity_id, prize_id),
    KEY idx_activity_prize_prize_id (prize_id),
    CONSTRAINT chk_activity_prize_weight CHECK (weight > 0),
    CONSTRAINT chk_activity_prize_stock CHECK (remaining_stock <= total_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动奖品表';

CREATE TABLE lottery_draw_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id VARCHAR(64) NOT NULL COMMENT '请求唯一标识',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    draw_count INT UNSIGNED NOT NULL COMMENT '抽奖次数',
    draw_date DATE NOT NULL COMMENT '上海时区抽奖日期',
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING' COMMENT '订单状态',
    fail_reason VARCHAR(500) NULL COMMENT '失败原因',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lottery_draw_order_request_id (request_id),
    KEY idx_draw_order_user_id (user_id),
    KEY idx_draw_order_activity_id (activity_id),
    KEY idx_draw_order_status (status),
    KEY idx_draw_order_created_at (created_at),
    CONSTRAINT chk_draw_order_count CHECK (draw_count IN (1, 10)),
    CONSTRAINT chk_draw_order_status CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖订单表';

CREATE TABLE lottery_draw_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '抽奖订单ID',
    request_id VARCHAR(64) NOT NULL COMMENT '请求ID',
    sequence_no INT UNSIGNED NOT NULL COMMENT '结果序号',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    result_type VARCHAR(20) NOT NULL DEFAULT 'WIN',
    prize_id BIGINT UNSIGNED NULL COMMENT '奖品ID',
    prize_name VARCHAR(100) NULL,
    prize_type VARCHAR(30) NULL,
    prize_image_url VARCHAR(1000) NULL,
    reward_definition_id BIGINT UNSIGNED NULL,
    reward_type VARCHAR(30) NULL,
    reward_target_id BIGINT UNSIGNED NULL,
    reward_quantity BIGINT UNSIGNED NULL,
    reward_payload JSON NULL,
    reward_fingerprint CHAR(64) NULL,
    draw_time DATETIME(3) NOT NULL COMMENT '抽奖时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_draw_record_request_sequence (request_id, sequence_no),
    KEY idx_draw_record_order_id (order_id),
    KEY idx_draw_record_user_id (user_id),
    KEY idx_draw_record_activity_id (activity_id),
    KEY idx_draw_record_prize_id (prize_id),
    KEY idx_draw_record_draw_time (draw_time),
    KEY idx_draw_record_reward_definition (reward_definition_id),
    CONSTRAINT chk_draw_record_sequence CHECK (sequence_no > 0),
    CONSTRAINT chk_draw_record_reward_type CHECK (
        reward_type IS NULL OR reward_type IN ('PRODUCT','COUPON','POINTS','MEMBERSHIP','DRAW_CHANCE')
    ),
    CONSTRAINT chk_draw_record_reward_quantity CHECK (reward_quantity IS NULL OR reward_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='中奖记录表';

CREATE TABLE user_benefit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    draw_record_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    prize_id BIGINT UNSIGNED NOT NULL COMMENT '奖品ID',
    prize_type VARCHAR(30) NOT NULL,
    reward_definition_id BIGINT UNSIGNED NULL,
    reward_type VARCHAR(30) NULL,
    reward_target_id BIGINT UNSIGNED NULL,
    reward_quantity BIGINT UNSIGNED NULL,
    reward_payload JSON NULL,
    reward_fingerprint CHAR(64) NULL,
    fulfillment_no VARCHAR(64) NULL,
    quantity INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '数量',
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE' COMMENT '权益状态',
    grant_error VARCHAR(500) NULL,
    obtained_at DATETIME(3) NOT NULL COMMENT '获得时间',
    expire_at DATETIME(3) NULL COMMENT '过期时间',
    claim_deadline DATETIME(3) NULL,
    claimed_at DATETIME(3) NULL,
    shipping_order_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_benefit_draw_record (draw_record_id),
    UNIQUE KEY uk_user_benefit_fulfillment (fulfillment_no),
    KEY idx_user_benefit_user_id (user_id),
    KEY idx_user_benefit_prize_id (prize_id),
    KEY idx_user_benefit_status (status),
    KEY idx_user_benefit_obtained_at (obtained_at),
    KEY idx_user_benefit_reward_definition (reward_definition_id),
    KEY idx_user_benefit_claim_expiry (status, claim_deadline, id),
    CONSTRAINT chk_user_benefit_quantity CHECK (quantity > 0),
    CONSTRAINT chk_user_benefit_expire_time CHECK (expire_at IS NULL OR expire_at > obtained_at),
    CONSTRAINT chk_user_benefit_reward_type CHECK (
        reward_type IS NULL OR reward_type IN ('PRODUCT','COUPON','POINTS','MEMBERSHIP','DRAW_CHANCE')
    ),
    CONSTRAINT chk_user_benefit_reward_quantity CHECK (reward_quantity IS NULL OR reward_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户权益表';

CREATE TABLE message_outbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '事件唯一标识',
    event_type VARCHAR(100) NOT NULL COMMENT '事件类型',
    event_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '事件版本',
    aggregate_type VARCHAR(100) NOT NULL COMMENT '聚合类型',
    aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合标识',
    payload JSON NOT NULL COMMENT '事件载荷',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数',
    last_error VARCHAR(500) NULL COMMENT '最近一次投递失败的安全错误',
    claim_token VARCHAR(64) NULL COMMENT '当前投递租约令牌',
    next_retry_at DATETIME(3) NULL COMMENT '下次重试或租约到期时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    sent_at DATETIME(3) NULL COMMENT '发送时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_outbox_event_id (event_id),
    KEY idx_message_outbox_status_retry (status, next_retry_at),
    CONSTRAINT chk_message_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可靠事件发件箱';

CREATE TABLE message_consume_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '事件唯一标识',
    consumer_name VARCHAR(100) NOT NULL COMMENT '消费者名称',
    consumed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消费时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_consume_event_consumer (event_id, consumer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息消费幂等记录';

CREATE TABLE draw_chance_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    available_balance BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reserved_balance BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_draw_chance_account_user (user_id),
    CONSTRAINT chk_draw_chance_account_balances CHECK (available_balance >= 0 AND reserved_balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖励抽奖次数账户';

CREATE TABLE draw_chance_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    available_after BIGINT UNSIGNED NOT NULL,
    reserved_after BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_draw_chance_ledger_business (business_type, business_id),
    KEY idx_draw_chance_ledger_user_created (user_id, created_at, id),
    CONSTRAINT chk_draw_chance_ledger_business_type CHECK (
        business_type IN ('LOTTERY_REWARD','DRAW_CONSUME','DRAW_RELEASE','MANUAL_ADJUSTMENT')
    ),
    CONSTRAINT chk_draw_chance_ledger_direction CHECK (direction IN ('CREDIT','DEBIT')),
    CONSTRAINT chk_draw_chance_ledger_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖励抽奖次数不可变流水';

CREATE TABLE draw_chance_reservation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    activity_id BIGINT UNSIGNED NOT NULL,
    draw_date DATE NOT NULL,
    draw_count INT UNSIGNED NOT NULL,
    bonus_reserved BIGINT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    settled_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_draw_chance_reservation_request (request_id),
    KEY idx_draw_chance_reservation_user_date (user_id, draw_date, status, id),
    KEY idx_draw_chance_reservation_reconcile (status, created_at, id),
    CONSTRAINT chk_draw_chance_reservation_count CHECK (draw_count IN (1,10)),
    CONSTRAINT chk_draw_chance_reservation_status CHECK (status IN ('RESERVED','CONFIRMED','RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖励抽奖次数预留';

CREATE TABLE lottery_reward_quarantine (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    order_id BIGINT UNSIGNED NULL,
    draw_record_id BIGINT UNSIGNED NULL,
    benefit_id BIGINT UNSIGNED NULL,
    prize_id BIGINT UNSIGNED NULL,
    reward_definition_id BIGINT UNSIGNED NULL,
    reason_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    quarantined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3) NULL,
    resolved_by BIGINT UNSIGNED NULL,
    resolution_note VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lottery_reward_quarantine_event (event_id),
    KEY idx_lottery_reward_quarantine_status (status, quarantined_at, id),
    CONSTRAINT chk_lottery_reward_quarantine_status CHECK (status IN ('OPEN','RESOLVED','IGNORED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖奖励事件隔离';
