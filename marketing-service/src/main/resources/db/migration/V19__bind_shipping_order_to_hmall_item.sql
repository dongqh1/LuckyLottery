ALTER TABLE shipping_order
    ADD COLUMN hmall_item_id BIGINT UNSIGNED NULL AFTER address_snapshot_id,
    ADD KEY idx_shipping_order_hmall_item (hmall_item_id);