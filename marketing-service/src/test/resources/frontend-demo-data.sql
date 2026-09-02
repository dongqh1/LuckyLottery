-- LuckyHub 本地前端演示数据。
-- 只能由 FrontendDemoDataSeederTests 在显式安全守卫通过后执行。
-- 约束：只管理 demo_ 用户和 DEMO- 业务自然键；限定 DELETE 只用于恢复演示角色/空状态，绝不删除用户。

INSERT INTO sys_user (username, password, nickname, status)
VALUES ('demo_admin', @demo_admin_password_hash, '演示管理员', 1)
ON DUPLICATE KEY UPDATE
    password = @demo_admin_password_hash,
    nickname = '演示管理员',
    status = 1;

INSERT INTO sys_user (username, password, nickname, status)
VALUES ('demo_user', @demo_user_password_hash, '已有资产的演示用户', 1)
ON DUPLICATE KEY UPDATE
    password = @demo_user_password_hash,
    nickname = '已有资产的演示用户',
    status = 1;

INSERT INTO sys_user (username, password, nickname, status)
VALUES ('demo_new', @demo_new_password_hash, '空状态演示用户', 1)
ON DUPLICATE KEY UPDATE
    password = @demo_new_password_hash,
    nickname = '空状态演示用户',
    status = 1;

-- 只清理三个演示账号自己的错误角色；绝不触碰其他用户关系。
DELETE ur
FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
JOIN sys_role r ON r.id = ur.role_id
WHERE (u.username = 'demo_admin' AND r.role_code NOT IN ('ADMIN', 'USER'))
   OR (u.username IN ('demo_user', 'demo_new') AND r.role_code <> 'USER');

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('ADMIN', 'USER')
WHERE u.username = 'demo_admin';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'USER'
WHERE u.username IN ('demo_user', 'demo_new');

-- 三种商品：正常实物、正常虚拟商品、售罄商品。
INSERT INTO product (product_code, product_name, product_type, image_url, description, status)
VALUES ('DEMO-PRODUCT-PHYSICAL', 'DEMO-幸运礼盒', 'PHYSICAL', NULL,
        'DEMO-可用现金或积分兑换的实物商品', 1)
ON DUPLICATE KEY UPDATE
    product_name = 'DEMO-幸运礼盒', product_type = 'PHYSICAL',
    description = 'DEMO-可用现金或积分兑换的实物商品', status = 1;

INSERT INTO product (product_code, product_name, product_type, image_url, description, status)
VALUES ('DEMO-PRODUCT-VIRTUAL', 'DEMO-数字纪念卡', 'VIRTUAL', NULL,
        'DEMO-无需收货地址的虚拟商品', 1)
ON DUPLICATE KEY UPDATE
    product_name = 'DEMO-数字纪念卡', product_type = 'VIRTUAL',
    description = 'DEMO-无需收货地址的虚拟商品', status = 1;

INSERT INTO product (product_code, product_name, product_type, image_url, description, status)
VALUES ('DEMO-PRODUCT-SOLD-OUT', 'DEMO-已售罄限定礼', 'PHYSICAL', NULL,
        'DEMO-用于验证售罄提示', 1)
ON DUPLICATE KEY UPDATE
    product_name = 'DEMO-已售罄限定礼', product_type = 'PHYSICAL',
    description = 'DEMO-用于验证售罄提示', status = 1;

INSERT INTO product_sku (product_id, sku_code, sku_name, cash_price_cent, points_price,
                         cash_enabled, points_enabled, status)
SELECT id, 'DEMO-SKU-PHYSICAL', '标准礼盒', 12900, 8000, 1, 1, 1
FROM product WHERE product_code = 'DEMO-PRODUCT-PHYSICAL'
ON DUPLICATE KEY UPDATE
    product_id = (SELECT id FROM product WHERE product_code = 'DEMO-PRODUCT-PHYSICAL'),
    sku_name = '标准礼盒', cash_price_cent = 12900, points_price = 8000,
    cash_enabled = 1, points_enabled = 1, status = 1;

INSERT INTO product_sku (product_id, sku_code, sku_name, cash_price_cent, points_price,
                         cash_enabled, points_enabled, status)
SELECT id, 'DEMO-SKU-VIRTUAL', '数字版', 2900, 1800, 1, 1, 1
FROM product WHERE product_code = 'DEMO-PRODUCT-VIRTUAL'
ON DUPLICATE KEY UPDATE
    product_id = (SELECT id FROM product WHERE product_code = 'DEMO-PRODUCT-VIRTUAL'),
    sku_name = '数字版', cash_price_cent = 2900, points_price = 1800,
    cash_enabled = 1, points_enabled = 1, status = 1;

INSERT INTO product_sku (product_id, sku_code, sku_name, cash_price_cent, points_price,
                         cash_enabled, points_enabled, status)
SELECT id, 'DEMO-SKU-SOLD-OUT', '限定版', 19900, 12000, 1, 1, 1
FROM product WHERE product_code = 'DEMO-PRODUCT-SOLD-OUT'
ON DUPLICATE KEY UPDATE
    product_id = (SELECT id FROM product WHERE product_code = 'DEMO-PRODUCT-SOLD-OUT'),
    sku_name = '限定版', cash_price_cent = 19900, points_price = 12000,
    cash_enabled = 1, points_enabled = 1, status = 1;

INSERT INTO sku_inventory (sku_id, total_stock, allocated_stock)
SELECT id, IF(sku_code = 'DEMO-SKU-SOLD-OUT', 0, 200),
       IF(sku_code = 'DEMO-SKU-SOLD-OUT', 0, 160)
FROM product_sku WHERE sku_code LIKE 'DEMO-SKU-%'
ON DUPLICATE KEY UPDATE
    total_stock = IF((SELECT sku_code FROM product_sku WHERE id = sku_id) = 'DEMO-SKU-SOLD-OUT', 0, 200),
    allocated_stock = IF((SELECT sku_code FROM product_sku WHERE id = sku_id) = 'DEMO-SKU-SOLD-OUT', 0, 160);

INSERT INTO inventory_channel_stock
    (sku_id, channel_code, allocated_stock, available_stock, reserved_stock, consumed_stock)
SELECT id, 'MALL', IF(sku_code = 'DEMO-SKU-SOLD-OUT', 0, 80),
       IF(sku_code = 'DEMO-SKU-SOLD-OUT', 0, 73), 2, 5
FROM product_sku WHERE sku_code IN ('DEMO-SKU-PHYSICAL', 'DEMO-SKU-VIRTUAL')
ON DUPLICATE KEY UPDATE
    allocated_stock = 80, available_stock = 73, reserved_stock = 2, consumed_stock = 5;

INSERT INTO inventory_channel_stock
    (sku_id, channel_code, allocated_stock, available_stock, reserved_stock, consumed_stock)
SELECT id, 'POINTS', 80, 72, 3, 5
FROM product_sku WHERE sku_code IN ('DEMO-SKU-PHYSICAL', 'DEMO-SKU-VIRTUAL')
ON DUPLICATE KEY UPDATE
    allocated_stock = 80, available_stock = 72, reserved_stock = 3, consumed_stock = 5;

INSERT INTO inventory_channel_stock
    (sku_id, channel_code, allocated_stock, available_stock, reserved_stock, consumed_stock)
SELECT id, 'MALL', 0, 0, 0, 0 FROM product_sku WHERE sku_code = 'DEMO-SKU-SOLD-OUT'
ON DUPLICATE KEY UPDATE
    allocated_stock = 0, available_stock = 0, reserved_stock = 0, consumed_stock = 0;

INSERT INTO inventory_channel_stock
    (sku_id, channel_code, allocated_stock, available_stock, reserved_stock, consumed_stock)
SELECT id, 'POINTS', 0, 0, 0, 0 FROM product_sku WHERE sku_code = 'DEMO-SKU-SOLD-OUT'
ON DUPLICATE KEY UPDATE
    allocated_stock = 0, available_stock = 0, reserved_stock = 0, consumed_stock = 0;

-- 会员、优惠券与积分资产仅给 demo_user；demo_new 保持空状态。
INSERT INTO membership_product
    (product_code, product_name, membership_level, card_type, duration_days, price_cent,
     discount_basis_points, daily_draw_bonus, points_multiplier_basis_points, status)
VALUES ('DEMO-MEMBER-YEAR', 'DEMO-年度幸运会员', 'GOLD', 'YEAR', 365, 9900,
        9000, 2, 12000, 1)
ON DUPLICATE KEY UPDATE
    product_name = 'DEMO-年度幸运会员', membership_level = 'GOLD', card_type = 'YEAR',
    duration_days = 365, price_cent = 9900, discount_basis_points = 9000,
    daily_draw_bonus = 2, points_multiplier_basis_points = 12000, status = 1;

INSERT INTO user_membership
    (user_id, membership_level, starts_at, expires_at, discount_basis_points,
     daily_draw_bonus, points_multiplier_basis_points)
SELECT id, 'GOLD', DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY),
       DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 335 DAY), 9000, 2, 12000
FROM sys_user WHERE username = 'demo_user'
ON DUPLICATE KEY UPDATE
    membership_level = 'GOLD', starts_at = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY),
    expires_at = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 335 DAY),
    discount_basis_points = 9000, daily_draw_bonus = 2,
    points_multiplier_basis_points = 12000;

INSERT INTO coupon_template
    (template_code, template_name, coupon_type, threshold_cent, discount_cent,
     applicable_product_id, valid_from, valid_until, per_user_limit,
     stackable_with_membership, status)
SELECT 'DEMO-COUPON-500', 'DEMO-满百减五元', 'THRESHOLD', 10000, 500,
       id, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY),
       DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 365 DAY), 10, 1, 1
FROM product WHERE product_code = 'DEMO-PRODUCT-PHYSICAL'
ON DUPLICATE KEY UPDATE
    template_name = 'DEMO-满百减五元', threshold_cent = 10000, discount_cent = 500,
    applicable_product_id = (SELECT id FROM product WHERE product_code = 'DEMO-PRODUCT-PHYSICAL'),
    valid_from = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY),
    valid_until = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 365 DAY), status = 1;

INSERT INTO user_coupon
    (coupon_no, template_id, user_id, status, valid_from, valid_until)
SELECT 'DEMO-COUPON-USER-001', t.id, u.id, 'AVAILABLE',
       DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY),
       DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 365 DAY)
FROM coupon_template t JOIN sys_user u ON u.username = 'demo_user'
WHERE t.template_code = 'DEMO-COUPON-500'
ON DUPLICATE KEY UPDATE
    template_id = (SELECT id FROM coupon_template WHERE template_code = 'DEMO-COUPON-500'),
    user_id = (SELECT id FROM sys_user WHERE username = 'demo_user'),
    status = 'AVAILABLE', locked_order_no = NULL, used_order_no = NULL,
    valid_from = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY),
    valid_until = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 365 DAY);

INSERT INTO points_account (user_id, balance)
SELECT id, CASE username WHEN 'demo_admin' THEN 5000 WHEN 'demo_user' THEN 20000 ELSE 0 END
FROM sys_user WHERE username IN ('demo_admin', 'demo_user')
ON DUPLICATE KEY UPDATE
    balance = CASE (SELECT username FROM sys_user WHERE id = user_id)
        WHEN 'demo_admin' THEN 5000 WHEN 'demo_user' THEN 20000 ELSE 0 END;

INSERT IGNORE INTO points_account (user_id, balance)
SELECT id, 0 FROM sys_user WHERE username = 'demo_new';

-- 旧版本曾给 demo_new 写入一条零资产演示流水；按精确自然键清理，恢复真正空历史。
DELETE l
FROM points_ledger l
JOIN sys_user u ON u.id = l.user_id
WHERE u.username = 'demo_new'
  AND l.business_type = 'MANUAL_ADJUSTMENT'
  AND l.business_id = 'DEMO-POINTS-INITIAL-demo_new';

INSERT INTO points_ledger
    (user_id, business_type, business_id, direction, amount, balance_after, remark)
SELECT id, 'MANUAL_ADJUSTMENT', CONCAT('DEMO-POINTS-INITIAL-', username),
       'CREDIT', CASE username WHEN 'demo_admin' THEN 5000 WHEN 'demo_user' THEN 20000 ELSE 1 END,
       CASE username WHEN 'demo_admin' THEN 5000 WHEN 'demo_user' THEN 20000 ELSE 0 END,
       'DEMO-本地前端演示初始积分'
FROM sys_user WHERE username IN ('demo_admin', 'demo_user')
ON DUPLICATE KEY UPDATE
    user_id = (SELECT id FROM sys_user WHERE username = SUBSTRING(business_id, 21)),
    remark = 'DEMO-本地前端演示初始积分';

-- 抽奖活动与两种奖励：可领取实物、已到账积分。
INSERT INTO reward_definition
    (reward_code, reward_name, reward_type, target_id, quantity, config_snapshot, status)
SELECT 'DEMO-REWARD-PHYSICAL', 'DEMO-幸运礼盒奖励', 'PRODUCT', id, 1,
       JSON_OBJECT('skuCode', 'DEMO-SKU-PHYSICAL'), 1
FROM product_sku WHERE sku_code = 'DEMO-SKU-PHYSICAL'
ON DUPLICATE KEY UPDATE
    reward_name = 'DEMO-幸运礼盒奖励', reward_type = 'PRODUCT',
    target_id = (SELECT id FROM product_sku WHERE sku_code = 'DEMO-SKU-PHYSICAL'),
    quantity = 1, config_snapshot = JSON_OBJECT('skuCode', 'DEMO-SKU-PHYSICAL'), status = 1;

INSERT INTO reward_definition
    (reward_code, reward_name, reward_type, target_id, quantity, config_snapshot, status)
VALUES ('DEMO-REWARD-POINTS', 'DEMO-500积分奖励', 'POINTS', NULL, 500,
        JSON_OBJECT('points', 500), 1)
ON DUPLICATE KEY UPDATE
    reward_name = 'DEMO-500积分奖励', reward_type = 'POINTS', target_id = NULL,
    quantity = 500, config_snapshot = JSON_OBJECT('points', 500), status = 1;

INSERT INTO marketing_prize
    (prize_name, prize_type, reward_definition_id, prize_level, image_url,
     description, stackable, status)
SELECT 'DEMO-幸运礼盒', 'PHYSICAL', id, 'FIRST', NULL,
       'DEMO-PRIZE-PHYSICAL', 0, 1
FROM reward_definition WHERE reward_code = 'DEMO-REWARD-PHYSICAL'
  AND NOT EXISTS (SELECT 1 FROM marketing_prize WHERE description = 'DEMO-PRIZE-PHYSICAL');

UPDATE marketing_prize
SET prize_name = 'DEMO-幸运礼盒', prize_type = 'PHYSICAL',
    reward_definition_id = (SELECT id FROM reward_definition WHERE reward_code = 'DEMO-REWARD-PHYSICAL'),
    prize_level = 'FIRST', status = 1
WHERE description = 'DEMO-PRIZE-PHYSICAL';

INSERT INTO marketing_prize
    (prize_name, prize_type, reward_definition_id, prize_level, image_url,
     description, stackable, status)
SELECT 'DEMO-500积分', 'POINTS', id, 'SECOND', NULL,
       'DEMO-PRIZE-POINTS', 1, 1
FROM reward_definition WHERE reward_code = 'DEMO-REWARD-POINTS'
  AND NOT EXISTS (SELECT 1 FROM marketing_prize WHERE description = 'DEMO-PRIZE-POINTS');

UPDATE marketing_prize
SET prize_name = 'DEMO-500积分', prize_type = 'POINTS',
    reward_definition_id = (SELECT id FROM reward_definition WHERE reward_code = 'DEMO-REWARD-POINTS'),
    prize_level = 'SECOND', status = 1
WHERE description = 'DEMO-PRIZE-POINTS';

INSERT INTO marketing_activity
    (activity_name, description, status, start_time, end_time, daily_limit,
     no_win_weight, created_by)
SELECT 'DEMO-每日幸运抽奖', 'DEMO-包含实物、积分和未中奖结果的本地活动', 'RUNNING',
       DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY),
       DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 365 DAY), 10, 60, id
FROM sys_user WHERE username = 'demo_admin'
  AND NOT EXISTS (SELECT 1 FROM marketing_activity WHERE activity_name = 'DEMO-每日幸运抽奖');

UPDATE marketing_activity
SET description = 'DEMO-包含实物、积分和未中奖结果的本地活动', status = 'RUNNING',
    start_time = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY),
    end_time = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 365 DAY),
    daily_limit = 10, no_win_weight = 60,
    created_by = (SELECT id FROM sys_user WHERE username = 'demo_admin')
WHERE activity_name = 'DEMO-每日幸运抽奖';

INSERT INTO marketing_activity_prize
    (activity_id, prize_id, weight, total_stock, remaining_stock, sort_order)
SELECT a.id, p.id,
       IF(p.description = 'DEMO-PRIZE-PHYSICAL', 10, 30),
       IF(p.description = 'DEMO-PRIZE-PHYSICAL', 20, 1000),
       IF(p.description = 'DEMO-PRIZE-PHYSICAL', 18, 999),
       IF(p.description = 'DEMO-PRIZE-PHYSICAL', 1, 2)
FROM marketing_activity a
JOIN marketing_prize p ON p.description IN ('DEMO-PRIZE-PHYSICAL', 'DEMO-PRIZE-POINTS')
WHERE a.activity_name = 'DEMO-每日幸运抽奖'
ON DUPLICATE KEY UPDATE
    weight = IF((SELECT description FROM marketing_prize WHERE id = prize_id) = 'DEMO-PRIZE-PHYSICAL', 10, 30),
    total_stock = IF((SELECT description FROM marketing_prize WHERE id = prize_id) = 'DEMO-PRIZE-PHYSICAL', 20, 1000),
    remaining_stock = IF((SELECT description FROM marketing_prize WHERE id = prize_id) = 'DEMO-PRIZE-PHYSICAL', 18, 999);

INSERT INTO lottery_draw_order
    (request_id, user_id, activity_id, draw_count, draw_date, status, completed_at)
SELECT 'DEMO-DRAW-PHYSICAL-001', u.id, a.id, 1,
       CURRENT_DATE(), 'SUCCESS', CURRENT_TIMESTAMP(3)
FROM sys_user u JOIN marketing_activity a ON a.activity_name = 'DEMO-每日幸运抽奖'
WHERE u.username = 'demo_user'
ON DUPLICATE KEY UPDATE
    user_id = (SELECT id FROM sys_user WHERE username = 'demo_user'),
    activity_id = (SELECT id FROM marketing_activity WHERE activity_name = 'DEMO-每日幸运抽奖'),
    draw_date = CURRENT_DATE(), status = 'SUCCESS', completed_at = CURRENT_TIMESTAMP(3);

INSERT INTO lottery_draw_order
    (request_id, user_id, activity_id, draw_count, draw_date, status, completed_at)
SELECT 'DEMO-DRAW-POINTS-001', u.id, a.id, 1,
       CURRENT_DATE(), 'SUCCESS', CURRENT_TIMESTAMP(3)
FROM sys_user u JOIN marketing_activity a ON a.activity_name = 'DEMO-每日幸运抽奖'
WHERE u.username = 'demo_user'
ON DUPLICATE KEY UPDATE
    user_id = (SELECT id FROM sys_user WHERE username = 'demo_user'),
    activity_id = (SELECT id FROM marketing_activity WHERE activity_name = 'DEMO-每日幸运抽奖'),
    draw_date = CURRENT_DATE(), status = 'SUCCESS', completed_at = CURRENT_TIMESTAMP(3);

INSERT INTO lottery_draw_record
    (order_id, request_id, sequence_no, user_id, activity_id, result_type,
     prize_id, prize_name, prize_type, prize_image_url, reward_definition_id,
     reward_type, reward_target_id, reward_quantity, reward_payload,
     reward_fingerprint, draw_time)
SELECT o.id, o.request_id, 1, o.user_id, o.activity_id, 'WIN', p.id,
       p.prize_name, p.prize_type, p.image_url, r.id, r.reward_type, r.target_id,
       r.quantity, r.config_snapshot, SHA2(CONCAT(r.reward_code, ':1'), 256), CURRENT_TIMESTAMP(3)
FROM lottery_draw_order o
JOIN marketing_prize p ON p.description = 'DEMO-PRIZE-PHYSICAL'
JOIN reward_definition r ON r.reward_code = 'DEMO-REWARD-PHYSICAL'
WHERE o.request_id = 'DEMO-DRAW-PHYSICAL-001'
ON DUPLICATE KEY UPDATE
    prize_id = (SELECT id FROM marketing_prize WHERE description = 'DEMO-PRIZE-PHYSICAL'),
    prize_name = 'DEMO-幸运礼盒', prize_type = 'PHYSICAL';

INSERT INTO lottery_draw_record
    (order_id, request_id, sequence_no, user_id, activity_id, result_type,
     prize_id, prize_name, prize_type, prize_image_url, reward_definition_id,
     reward_type, reward_target_id, reward_quantity, reward_payload,
     reward_fingerprint, draw_time)
SELECT o.id, o.request_id, 1, o.user_id, o.activity_id, 'WIN', p.id,
       p.prize_name, p.prize_type, p.image_url, r.id, r.reward_type, r.target_id,
       r.quantity, r.config_snapshot, SHA2(CONCAT(r.reward_code, ':500'), 256), CURRENT_TIMESTAMP(3)
FROM lottery_draw_order o
JOIN marketing_prize p ON p.description = 'DEMO-PRIZE-POINTS'
JOIN reward_definition r ON r.reward_code = 'DEMO-REWARD-POINTS'
WHERE o.request_id = 'DEMO-DRAW-POINTS-001'
ON DUPLICATE KEY UPDATE
    prize_id = (SELECT id FROM marketing_prize WHERE description = 'DEMO-PRIZE-POINTS'),
    prize_name = 'DEMO-500积分', prize_type = 'POINTS';

INSERT INTO user_benefit
    (draw_record_id, user_id, prize_id, prize_type, reward_definition_id,
     reward_type, reward_target_id, reward_quantity, reward_payload,
     reward_fingerprint, fulfillment_no, quantity, status, obtained_at,
     expire_at, claim_deadline)
SELECT d.id, d.user_id, d.prize_id, d.prize_type, d.reward_definition_id,
       d.reward_type, d.reward_target_id, d.reward_quantity, d.reward_payload,
       d.reward_fingerprint, 'DEMO-BENEFIT-PHYSICAL-001', 1, 'CLAIM_PENDING',
       CURRENT_TIMESTAMP(3), DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 365 DAY),
       DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY)
FROM lottery_draw_record d
WHERE d.request_id = 'DEMO-DRAW-PHYSICAL-001' AND d.sequence_no = 1
ON DUPLICATE KEY UPDATE
    user_id = (SELECT id FROM sys_user WHERE username = 'demo_user'),
    status = 'CLAIM_PENDING', shipping_order_id = NULL, claimed_at = NULL,
    claim_deadline = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY);

INSERT INTO user_benefit
    (draw_record_id, user_id, prize_id, prize_type, reward_definition_id,
     reward_type, reward_target_id, reward_quantity, reward_payload,
     reward_fingerprint, fulfillment_no, quantity, status, obtained_at, expire_at)
SELECT d.id, d.user_id, d.prize_id, d.prize_type, d.reward_definition_id,
       d.reward_type, d.reward_target_id, d.reward_quantity, d.reward_payload,
       d.reward_fingerprint, 'DEMO-BENEFIT-POINTS-001', 1, 'AVAILABLE',
       CURRENT_TIMESTAMP(3), DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 365 DAY)
FROM lottery_draw_record d
WHERE d.request_id = 'DEMO-DRAW-POINTS-001' AND d.sequence_no = 1
ON DUPLICATE KEY UPDATE
    user_id = (SELECT id FROM sys_user WHERE username = 'demo_user'),
    status = 'AVAILABLE';

-- 两笔现金订单：已支付实物单 + 待支付虚拟单。
INSERT INTO mall_order
    (order_no, user_id, sku_id, quantity, product_code, product_name, sku_code,
     sku_name, product_type, image_url, unit_price_cent, original_amount_cent,
     membership_level, membership_discount_basis_points, membership_discount_cent,
     amount_after_membership_cent, coupon_discount_cent, payable_amount_cent,
     status, payment_deadline, paid_at)
SELECT 'DEMO-ORDER-PAID-001', u.id, s.id, 1, p.product_code, p.product_name,
       s.sku_code, s.sku_name, p.product_type, p.image_url, 12900, 12900,
       'GOLD', 9000, 1290, 11610, 0, 11610, 'PAID',
       DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY), CURRENT_TIMESTAMP(3)
FROM sys_user u
JOIN product p ON p.product_code = 'DEMO-PRODUCT-PHYSICAL'
JOIN product_sku s ON s.product_id = p.id AND s.sku_code = 'DEMO-SKU-PHYSICAL'
WHERE u.username = 'demo_user'
ON DUPLICATE KEY UPDATE
    user_id = (SELECT id FROM sys_user WHERE username = 'demo_user'),
    sku_id = (SELECT id FROM product_sku WHERE sku_code = 'DEMO-SKU-PHYSICAL'),
    status = 'PAID', paid_at = CURRENT_TIMESTAMP(3), cancelled_at = NULL, cancel_reason = NULL;

INSERT INTO mall_order
    (order_no, user_id, sku_id, quantity, product_code, product_name, sku_code,
     sku_name, product_type, image_url, unit_price_cent, original_amount_cent,
     membership_level, membership_discount_basis_points, membership_discount_cent,
     amount_after_membership_cent, coupon_discount_cent, payable_amount_cent,
     status, payment_deadline)
SELECT 'DEMO-ORDER-PENDING-001', u.id, s.id, 1, p.product_code, p.product_name,
       s.sku_code, s.sku_name, p.product_type, p.image_url, 2900, 2900,
       'GOLD', 9000, 290, 2610, 0, 2610, 'PENDING_PAYMENT',
       DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY)
FROM sys_user u
JOIN product p ON p.product_code = 'DEMO-PRODUCT-VIRTUAL'
JOIN product_sku s ON s.product_id = p.id AND s.sku_code = 'DEMO-SKU-VIRTUAL'
WHERE u.username = 'demo_user'
ON DUPLICATE KEY UPDATE
    user_id = (SELECT id FROM sys_user WHERE username = 'demo_user'),
    sku_id = (SELECT id FROM product_sku WHERE sku_code = 'DEMO-SKU-VIRTUAL'),
    status = 'PENDING_PAYMENT', payment_deadline = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY),
    paid_at = NULL, cancelled_at = NULL, cancel_reason = NULL;

INSERT INTO payment_order
    (payment_no, order_no, user_id, amount_cent, status, callback_result, paid_at)
SELECT 'DEMO-PAY-SUCCESS-001', order_no, user_id, payable_amount_cent,
       'SUCCESS', 'SUCCESS', CURRENT_TIMESTAMP(3)
FROM mall_order WHERE order_no = 'DEMO-ORDER-PAID-001'
ON DUPLICATE KEY UPDATE
    order_no = 'DEMO-ORDER-PAID-001',
    user_id = (SELECT user_id FROM mall_order WHERE order_no = 'DEMO-ORDER-PAID-001'),
    amount_cent = (SELECT payable_amount_cent FROM mall_order WHERE order_no = 'DEMO-ORDER-PAID-001'),
    status = 'SUCCESS', callback_result = 'SUCCESS', paid_at = CURRENT_TIMESTAMP(3), failure_reason = NULL;

INSERT INTO payment_order
    (payment_no, order_no, user_id, amount_cent, status, callback_result)
SELECT 'DEMO-PAY-PENDING-001', order_no, user_id, payable_amount_cent, 'PENDING', NULL
FROM mall_order WHERE order_no = 'DEMO-ORDER-PENDING-001'
ON DUPLICATE KEY UPDATE
    order_no = 'DEMO-ORDER-PENDING-001',
    user_id = (SELECT user_id FROM mall_order WHERE order_no = 'DEMO-ORDER-PENDING-001'),
    amount_cent = (SELECT payable_amount_cent FROM mall_order WHERE order_no = 'DEMO-ORDER-PENDING-001'),
    status = 'PENDING', callback_result = NULL, paid_at = NULL, failure_reason = NULL;

-- 一笔积分兑换和两张不同状态的物流单。
INSERT INTO points_redemption_order
    (redemption_no, user_id, sku_id, quantity, unit_points, total_points,
     product_code, product_name, sku_code, sku_name, product_type, image_url, status)
SELECT 'DEMO-REDEEM-COMPLETED-001', u.id, s.id, 1, 8000, 8000,
       p.product_code, p.product_name, s.sku_code, s.sku_name, p.product_type,
       p.image_url, 'COMPLETED'
FROM sys_user u
JOIN product p ON p.product_code = 'DEMO-PRODUCT-PHYSICAL'
JOIN product_sku s ON s.product_id = p.id AND s.sku_code = 'DEMO-SKU-PHYSICAL'
WHERE u.username = 'demo_user'
ON DUPLICATE KEY UPDATE
    user_id = (SELECT id FROM sys_user WHERE username = 'demo_user'),
    sku_id = (SELECT id FROM product_sku WHERE sku_code = 'DEMO-SKU-PHYSICAL'),
    status = 'COMPLETED', reversal_no = NULL, failure_reason = NULL;

INSERT INTO user_shipping_address
    (user_id, receiver_ciphertext, phone_ciphertext, province_ciphertext,
     city_ciphertext, district_ciphertext, detail_ciphertext, receiver_masked,
     phone_masked, region_masked, is_default, status)
SELECT u.id, @demo_receiver_ciphertext, @demo_phone_ciphertext,
       @demo_province_ciphertext, @demo_city_ciphertext, @demo_district_ciphertext,
       @demo_detail_ciphertext, '演示*', '138****2026', 'DEMO-演示省 演示市 演示区', 1, 'ACTIVE'
FROM sys_user u
WHERE u.username = 'demo_user'
  AND NOT EXISTS (
      SELECT 1 FROM user_shipping_address a
      WHERE a.user_id = u.id AND a.region_masked = 'DEMO-演示省 演示市 演示区'
  );

UPDATE user_shipping_address
SET receiver_ciphertext = @demo_receiver_ciphertext,
    phone_ciphertext = @demo_phone_ciphertext,
    province_ciphertext = @demo_province_ciphertext,
    city_ciphertext = @demo_city_ciphertext,
    district_ciphertext = @demo_district_ciphertext,
    detail_ciphertext = @demo_detail_ciphertext,
    receiver_masked = '演示*', phone_masked = '138****2026',
    is_default = 1, status = 'ACTIVE', deleted_at = NULL
WHERE user_id = (SELECT id FROM sys_user WHERE username = 'demo_user')
  AND region_masked = 'DEMO-演示省 演示市 演示区';

INSERT INTO shipping_address_snapshot
    (snapshot_no, user_id, address_id, source_type, source_id,
     receiver_ciphertext, phone_ciphertext, province_ciphertext, city_ciphertext,
     district_ciphertext, detail_ciphertext, receiver_masked, phone_masked, region_masked)
SELECT 'DEMO-SNAPSHOT-CASH-001', o.user_id, NULL, 'CASH_ORDER', CAST(o.id AS CHAR),
       @demo_receiver_ciphertext, @demo_phone_ciphertext,
       @demo_province_ciphertext, @demo_city_ciphertext,
       @demo_district_ciphertext, @demo_detail_ciphertext,
       '演示*', '138****2026', '演示地区（仅脱敏展示）'
FROM mall_order o WHERE o.order_no = 'DEMO-ORDER-PAID-001'
ON DUPLICATE KEY UPDATE
    user_id = (SELECT user_id FROM mall_order WHERE order_no = 'DEMO-ORDER-PAID-001'),
    source_id = CAST((SELECT id FROM mall_order WHERE order_no = 'DEMO-ORDER-PAID-001') AS CHAR),
    receiver_ciphertext = @demo_receiver_ciphertext, phone_ciphertext = @demo_phone_ciphertext,
    province_ciphertext = @demo_province_ciphertext, city_ciphertext = @demo_city_ciphertext,
    district_ciphertext = @demo_district_ciphertext, detail_ciphertext = @demo_detail_ciphertext;

INSERT INTO shipping_address_snapshot
    (snapshot_no, user_id, address_id, source_type, source_id,
     receiver_ciphertext, phone_ciphertext, province_ciphertext, city_ciphertext,
     district_ciphertext, detail_ciphertext, receiver_masked, phone_masked, region_masked)
SELECT 'DEMO-SNAPSHOT-POINTS-001', o.user_id, NULL, 'POINTS_REDEMPTION', CAST(o.id AS CHAR),
       @demo_receiver_ciphertext, @demo_phone_ciphertext,
       @demo_province_ciphertext, @demo_city_ciphertext,
       @demo_district_ciphertext, @demo_detail_ciphertext,
       '演示*', '138****2026', '演示地区（仅脱敏展示）'
FROM points_redemption_order o WHERE o.redemption_no = 'DEMO-REDEEM-COMPLETED-001'
ON DUPLICATE KEY UPDATE
    user_id = (SELECT user_id FROM points_redemption_order WHERE redemption_no = 'DEMO-REDEEM-COMPLETED-001'),
    source_id = CAST((SELECT id FROM points_redemption_order WHERE redemption_no = 'DEMO-REDEEM-COMPLETED-001') AS CHAR),
    receiver_ciphertext = @demo_receiver_ciphertext, phone_ciphertext = @demo_phone_ciphertext,
    province_ciphertext = @demo_province_ciphertext, city_ciphertext = @demo_city_ciphertext,
    district_ciphertext = @demo_district_ciphertext, detail_ciphertext = @demo_detail_ciphertext;

INSERT INTO shipping_order
    (shipping_no, source_type, source_id, target_user_id, address_snapshot_id,
     sku_code, product_name, image_url, quantity, fulfillment_no, carrier_code,
     carrier_name, waybill_no, status, shipped_at, delivered_at)
SELECT 'DEMO-SHIP-DELIVERED-001', 'CASH_ORDER', CAST(o.id AS CHAR), o.user_id,
       s.id, o.sku_code, o.product_name, o.image_url, o.quantity,
       'DEMO-FULFILL-SHIP-CASH-001', 'DEMO', '演示快递', 'DEMO-WAYBILL-001',
       'DELIVERED', DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 2 DAY),
       DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY)
FROM mall_order o
JOIN shipping_address_snapshot s ON s.snapshot_no = 'DEMO-SNAPSHOT-CASH-001'
WHERE o.order_no = 'DEMO-ORDER-PAID-001'
ON DUPLICATE KEY UPDATE
    source_id = CAST((SELECT id FROM mall_order WHERE order_no = 'DEMO-ORDER-PAID-001') AS CHAR),
    target_user_id = (SELECT user_id FROM mall_order WHERE order_no = 'DEMO-ORDER-PAID-001'),
    address_snapshot_id = (SELECT id FROM shipping_address_snapshot WHERE snapshot_no = 'DEMO-SNAPSHOT-CASH-001'),
    status = 'DELIVERED', carrier_code = 'DEMO', carrier_name = '演示快递',
    waybill_no = 'DEMO-WAYBILL-001', delivered_at = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY);

INSERT INTO shipping_order
    (shipping_no, source_type, source_id, target_user_id, address_snapshot_id,
     sku_code, product_name, image_url, quantity, fulfillment_no, carrier_code,
     carrier_name, waybill_no, status, shipped_at)
SELECT 'DEMO-SHIP-IN-TRANSIT-001', 'POINTS_REDEMPTION', CAST(o.id AS CHAR), o.user_id,
       s.id, o.sku_code, o.product_name, o.image_url, o.quantity,
       'DEMO-FULFILL-SHIP-POINTS-001', 'DEMO', '演示快递', 'DEMO-WAYBILL-002',
       'IN_TRANSIT', DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY)
FROM points_redemption_order o
JOIN shipping_address_snapshot s ON s.snapshot_no = 'DEMO-SNAPSHOT-POINTS-001'
WHERE o.redemption_no = 'DEMO-REDEEM-COMPLETED-001'
ON DUPLICATE KEY UPDATE
    source_id = CAST((SELECT id FROM points_redemption_order WHERE redemption_no = 'DEMO-REDEEM-COMPLETED-001') AS CHAR),
    target_user_id = (SELECT user_id FROM points_redemption_order WHERE redemption_no = 'DEMO-REDEEM-COMPLETED-001'),
    address_snapshot_id = (SELECT id FROM shipping_address_snapshot WHERE snapshot_no = 'DEMO-SNAPSHOT-POINTS-001'),
    status = 'IN_TRANSIT', carrier_code = 'DEMO', carrier_name = '演示快递',
    waybill_no = 'DEMO-WAYBILL-002', delivered_at = NULL;

UPDATE mall_order
SET address_snapshot_id = (SELECT id FROM shipping_address_snapshot WHERE snapshot_no = 'DEMO-SNAPSHOT-CASH-001'),
    shipping_order_id = (SELECT id FROM shipping_order WHERE shipping_no = 'DEMO-SHIP-DELIVERED-001')
WHERE order_no = 'DEMO-ORDER-PAID-001';

UPDATE points_redemption_order
SET address_snapshot_id = (SELECT id FROM shipping_address_snapshot WHERE snapshot_no = 'DEMO-SNAPSHOT-POINTS-001'),
    shipping_order_id = (SELECT id FROM shipping_order WHERE shipping_no = 'DEMO-SHIP-IN-TRANSIT-001')
WHERE redemption_no = 'DEMO-REDEEM-COMPLETED-001';

INSERT INTO shipping_tracking_event
    (shipping_order_id, provider_event_id, waybill_no, event_type,
     location_summary, description, event_time, received_at)
SELECT id, 'DEMO-EVENT-001', waybill_no, 'PICKED_UP', '演示仓', '快件已揽收',
       DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 3 DAY), CURRENT_TIMESTAMP(3)
FROM shipping_order WHERE shipping_no = 'DEMO-SHIP-DELIVERED-001'
ON DUPLICATE KEY UPDATE description = '快件已揽收';

INSERT INTO shipping_tracking_event
    (shipping_order_id, provider_event_id, waybill_no, event_type,
     location_summary, description, event_time, received_at)
SELECT id, 'DEMO-EVENT-002', waybill_no, 'DELIVERED', '演示目的地', '快件已签收',
       DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY), CURRENT_TIMESTAMP(3)
FROM shipping_order WHERE shipping_no = 'DEMO-SHIP-DELIVERED-001'
ON DUPLICATE KEY UPDATE description = '快件已签收';

INSERT INTO shipping_tracking_event
    (shipping_order_id, provider_event_id, waybill_no, event_type,
     location_summary, description, event_time, received_at)
SELECT id, 'DEMO-EVENT-003', waybill_no, 'PICKED_UP', '演示仓', '快件已揽收',
       DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY), CURRENT_TIMESTAMP(3)
FROM shipping_order WHERE shipping_no = 'DEMO-SHIP-IN-TRANSIT-001'
ON DUPLICATE KEY UPDATE description = '快件已揽收';

INSERT INTO shipping_tracking_event
    (shipping_order_id, provider_event_id, waybill_no, event_type,
     location_summary, description, event_time, received_at)
SELECT id, 'DEMO-EVENT-004', waybill_no, 'IN_TRANSIT', '演示中转站', '运输途中',
       DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 12 HOUR), CURRENT_TIMESTAMP(3)
FROM shipping_order WHERE shipping_no = 'DEMO-SHIP-IN-TRANSIT-001'
ON DUPLICATE KEY UPDATE description = '运输途中';
