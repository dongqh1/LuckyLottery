ALTER TABLE membership_product
    ADD COLUMN sales_enabled TINYINT NOT NULL DEFAULT 1 AFTER status,
    ADD CONSTRAINT chk_membership_product_sales_enabled CHECK (sales_enabled IN (0,1));
