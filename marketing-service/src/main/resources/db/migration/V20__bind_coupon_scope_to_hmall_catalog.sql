ALTER TABLE coupon_template
    ADD COLUMN scope_type VARCHAR(20) NULL COMMENT 'new commerce scope: ALL, ITEM, CATEGORY' AFTER applicable_product_id,
    ADD COLUMN hmall_item_id BIGINT UNSIGNED NULL COMMENT 'hmall.item.id for ITEM scope' AFTER scope_type,
    ADD COLUMN hmall_category VARCHAR(100) NULL COMMENT 'hmall item category for CATEGORY scope' AFTER hmall_item_id,
    ADD KEY idx_coupon_template_hmall_item (hmall_item_id),
    ADD KEY idx_coupon_template_hmall_category (hmall_category);
