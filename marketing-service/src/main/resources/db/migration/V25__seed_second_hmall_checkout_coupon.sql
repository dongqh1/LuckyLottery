-- Keep one generally applicable coupon available after the first checkout coupon is used.
INSERT INTO coupon_template
    (template_code, template_name, coupon_type, threshold_cent, discount_cent,
     applicable_product_id, scope_type, hmall_item_id, hmall_category, hmall_brand,
     valid_from, valid_until, per_user_limit, stackable_with_membership, status)
VALUES
    ('HMALL-CHECKOUT-100', '商城满减券', 'THRESHOLD', 1000, 100,
     NULL, 'ALL', NULL, NULL, NULL,
     DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY),
     DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 10 YEAR), 1, 1, 1)
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name), coupon_type = VALUES(coupon_type),
    threshold_cent = VALUES(threshold_cent), discount_cent = VALUES(discount_cent),
    scope_type = VALUES(scope_type), valid_until = VALUES(valid_until),
    stackable_with_membership = VALUES(stackable_with_membership), status = 1;

INSERT IGNORE INTO user_coupon
    (coupon_no, template_id, user_id, status, valid_from, valid_until)
SELECT CONCAT(t.template_code, '-', u.id), t.id, u.id,
       'AVAILABLE', t.valid_from, t.valid_until
  FROM sys_user u
  JOIN coupon_template t ON t.template_code = 'HMALL-CHECKOUT-100'
 WHERE u.username LIKE 'hmall:%';

INSERT IGNORE INTO coupon_issue_record
    (business_no, template_id, user_id, user_coupon_id)
SELECT CONCAT('HMALL-AUTO-', coupon.coupon_no), coupon.template_id, coupon.user_id, coupon.id
  FROM user_coupon coupon
  JOIN coupon_template template ON template.id = coupon.template_id
 WHERE template.template_code = 'HMALL-CHECKOUT-100';