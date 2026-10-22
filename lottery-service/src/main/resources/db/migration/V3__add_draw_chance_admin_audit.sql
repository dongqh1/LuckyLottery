ALTER TABLE draw_chance_ledger
    ADD COLUMN operator_user_id BIGINT UNSIGNED NULL AFTER user_id,
    ADD COLUMN reason VARCHAR(255) NULL AFTER reserved_after;
