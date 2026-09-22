ALTER TABLE coupon_template
    ADD COLUMN hmall_brand VARCHAR(100) NULL COMMENT 'hmall item brand for BRAND scope' AFTER hmall_category,
    ADD KEY idx_coupon_template_hmall_brand (hmall_brand);

ALTER TABLE coupon_template
    MODIFY COLUMN scope_type VARCHAR(20) NULL COMMENT 'commerce scope: ALL, ITEM, CATEGORY, BRAND';
