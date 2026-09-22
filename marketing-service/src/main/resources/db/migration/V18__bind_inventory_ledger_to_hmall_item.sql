ALTER TABLE inventory_ledger
    MODIFY COLUMN sku_id BIGINT UNSIGNED NULL,
    ADD COLUMN hmall_item_id BIGINT UNSIGNED NULL AFTER sku_id,
    ADD KEY idx_inventory_ledger_hmall_item_created (hmall_item_id, created_at);

ALTER TABLE inventory_ledger
    ADD CONSTRAINT chk_inventory_ledger_item_identity CHECK (
        (sku_id IS NOT NULL AND hmall_item_id IS NULL)
        OR (sku_id IS NULL AND hmall_item_id IS NOT NULL)
    );