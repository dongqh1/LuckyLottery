-- Operational coupon assets for the integrated Hmall account.
INSERT INTO coupon_template
    (template_code, template_name, coupon_type, threshold_cent, discount_cent,
     applicable_product_id, scope_type, hmall_item_id, hmall_category, hmall_brand,
     valid_from, valid_until, per_user_limit, stackable_with_membership, status)
VALUES
    ('WELCOME-ALL-100', '全场礼遇券', 'NO_THRESHOLD', 0, 100,
     NULL, 'ALL', NULL, NULL, NULL,
     DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY), DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 10 YEAR), 1, 1, 1),
    ('RIMOWA-BRAND-200', 'RIMOWA 品牌专享券', 'NO_THRESHOLD', 0, 200,
     NULL, 'BRAND', NULL, NULL, 'RIMOWA',
     DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY), DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 10 YEAR), 1, 1, 1)
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name), coupon_type = VALUES(coupon_type),
    threshold_cent = VALUES(threshold_cent), discount_cent = VALUES(discount_cent),
    scope_type = VALUES(scope_type), hmall_item_id = VALUES(hmall_item_id),
    hmall_category = VALUES(hmall_category), hmall_brand = VALUES(hmall_brand),
    valid_until = VALUES(valid_until), stackable_with_membership = VALUES(stackable_with_membership), status = 1;

INSERT INTO user_coupon
    (coupon_no, template_id, user_id, status, valid_from, valid_until)
SELECT CONCAT('WELCOME-ALL-', u.id), t.id, u.id, 'AVAILABLE', t.valid_from, t.valid_until
FROM sys_user u
JOIN coupon_template t ON t.template_code = 'WELCOME-ALL-100'
WHERE u.username = 'hmall:1'
ON DUPLICATE KEY UPDATE template_id = VALUES(template_id), user_id = VALUES(user_id),
    valid_from = VALUES(valid_from), valid_until = VALUES(valid_until);

INSERT INTO user_coupon
    (coupon_no, template_id, user_id, status, valid_from, valid_until)
SELECT CONCAT('RIMOWA-BRAND-', u.id), t.id, u.id, 'AVAILABLE', t.valid_from, t.valid_until
FROM sys_user u
JOIN coupon_template t ON t.template_code = 'RIMOWA-BRAND-200'
WHERE u.username = 'hmall:1'
ON DUPLICATE KEY UPDATE template_id = VALUES(template_id), user_id = VALUES(user_id),
    valid_from = VALUES(valid_from), valid_until = VALUES(valid_until);

INSERT IGNORE INTO coupon_issue_record (business_no, template_id, user_id, user_coupon_id)
SELECT CONCAT('WELCOME-ALL-ISSUE-', u.id), t.id, u.id, c.id
FROM sys_user u
JOIN coupon_template t ON t.template_code = 'WELCOME-ALL-100'
JOIN user_coupon c ON c.coupon_no = CONCAT('WELCOME-ALL-', u.id)
WHERE u.username = 'hmall:1';

INSERT IGNORE INTO coupon_issue_record (business_no, template_id, user_id, user_coupon_id)
SELECT CONCAT('RIMOWA-BRAND-ISSUE-', u.id), t.id, u.id, c.id
FROM sys_user u
JOIN coupon_template t ON t.template_code = 'RIMOWA-BRAND-200'
JOIN user_coupon c ON c.coupon_no = CONCAT('RIMOWA-BRAND-', u.id)
WHERE u.username = 'hmall:1';
