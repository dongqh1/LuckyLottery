SELECT UTC_TIMESTAMP(3) AS audit_execution_utc;

SELECT VERSION() AS mysql_server_version;

SELECT expected.table_schema,
       expected.table_name,
       CASE WHEN actual.table_name IS NULL THEN 0 ELSE 1 END AS table_present
FROM (
    SELECT 'luckyhub' AS table_schema, 'membership_product' AS table_name
    UNION ALL SELECT 'luckyhub', 'user_membership'
    UNION ALL SELECT 'luckyhub', 'membership_grant_record'
    UNION ALL SELECT 'luckyhub', 'sys_user'
    UNION ALL SELECT 'luckyhub', 'reward_definition'
    UNION ALL SELECT 'luckyhub', 'lottery_draw_record'
    UNION ALL SELECT 'luckyhub', 'user_benefit'
    UNION ALL SELECT 'hm_user', 'user'
) expected
LEFT JOIN information_schema.tables actual
    ON actual.table_schema = expected.table_schema
    AND actual.table_name = expected.table_name
ORDER BY expected.table_schema, expected.table_name;

SELECT COUNT(*) AS membership_product_total,
       COALESCE(SUM(status = 1), 0) AS enabled_product_total,
       COALESCE(SUM(status = 0), 0) AS disabled_product_total,
       COALESCE(SUM(status NOT IN (0, 1) OR status IS NULL), 0) AS unexpected_product_status_total
FROM luckyhub.membership_product;

SELECT card_type,
       COUNT(*) AS product_total
FROM luckyhub.membership_product
GROUP BY card_type
ORDER BY card_type;

SELECT COUNT(*) AS user_membership_total,
       COALESCE(SUM(expires_at > CURRENT_TIMESTAMP(3)), 0) AS active_membership_total,
       COALESCE(SUM(expires_at <= CURRENT_TIMESTAMP(3)), 0) AS expired_membership_total,
       COALESCE(SUM(expires_at <= starts_at), 0) AS invalid_period_total,
       (
           SELECT COUNT(*)
           FROM (
               SELECT COUNT(*) AS duplicate_group_size
               FROM luckyhub.user_membership
               GROUP BY user_id
               HAVING COUNT(*) > 1
           ) duplicate_users
       ) AS duplicate_user_groups
FROM luckyhub.user_membership;

SELECT membership_level,
       COUNT(*) AS user_membership_total,
       COALESCE(SUM(expires_at > CURRENT_TIMESTAMP(3)), 0) AS active_membership_total,
       COALESCE(SUM(expires_at <= CURRENT_TIMESTAMP(3)), 0) AS expired_membership_total
FROM luckyhub.user_membership
GROUP BY membership_level
ORDER BY membership_level;

SELECT COUNT(*) AS membership_grant_record_total,
       COALESCE(SUM(expires_at <= starts_at), 0) AS invalid_grant_period_total,
       (
           SELECT COUNT(*)
           FROM (
               SELECT COUNT(*) AS duplicate_group_size
               FROM luckyhub.membership_grant_record
               GROUP BY business_no
               HAVING COUNT(*) > 1
           ) duplicate_business_keys
       ) AS duplicate_business_key_groups
FROM luckyhub.membership_grant_record;

SELECT COALESCE(SUM(CASE WHEN user_record.id IS NULL THEN 1 ELSE 0 END), 0) AS membership_missing_marketing_user_total
FROM luckyhub.user_membership membership_record
LEFT JOIN luckyhub.sys_user user_record ON user_record.id = membership_record.user_id;

SELECT COALESCE(SUM(CASE WHEN user_record.id IS NULL THEN 1 ELSE 0 END), 0) AS grant_missing_marketing_user_total,
       COALESCE(SUM(CASE WHEN product.id IS NULL THEN 1 ELSE 0 END), 0) AS grant_missing_membership_product_total
FROM luckyhub.membership_grant_record grant_record
LEFT JOIN luckyhub.sys_user user_record ON user_record.id = grant_record.user_id
LEFT JOIN luckyhub.membership_product product ON product.id = grant_record.membership_product_id;

SELECT COUNT(*) AS membership_reward_definition_total,
       COALESCE(SUM(reward.target_id IS NULL), 0) AS reward_definition_missing_product_reference_total,
       COALESCE(SUM(reward.target_id IS NOT NULL AND product.id IS NULL), 0) AS reward_definition_orphan_product_reference_total
FROM luckyhub.reward_definition reward
LEFT JOIN luckyhub.membership_product product ON product.id = reward.target_id
WHERE reward.reward_type = 'MEMBERSHIP';

SELECT COUNT(*) AS lottery_membership_snapshot_total,
       COALESCE(SUM(draw_record.reward_target_id IS NULL OR product.id IS NULL), 0) AS lottery_snapshot_missing_product_total,
       COALESCE(SUM(draw_record.reward_payload IS NULL), 0) AS lottery_snapshot_missing_payload_total,
       COALESCE(SUM(
           draw_record.reward_payload IS NOT NULL
           AND JSON_UNQUOTE(JSON_EXTRACT(draw_record.reward_payload, '$.membershipProductId')) IS NULL
       ), 0) AS lottery_snapshot_missing_payload_product_id_total,
       COALESCE(SUM(
           draw_record.reward_payload IS NOT NULL
           AND JSON_UNQUOTE(JSON_EXTRACT(draw_record.reward_payload, '$.membershipProductId')) IS NOT NULL
           AND CAST(JSON_UNQUOTE(JSON_EXTRACT(draw_record.reward_payload, '$.membershipProductId')) AS UNSIGNED)
               <> draw_record.reward_target_id
       ), 0) AS lottery_snapshot_target_payload_mismatch_total
FROM luckyhub.lottery_draw_record draw_record
LEFT JOIN luckyhub.membership_product product ON product.id = draw_record.reward_target_id
WHERE draw_record.reward_type = 'MEMBERSHIP';

SELECT COUNT(*) AS user_benefit_membership_snapshot_total,
       COALESCE(SUM(benefit.reward_target_id IS NULL OR product.id IS NULL), 0) AS user_benefit_snapshot_missing_product_total,
       COALESCE(SUM(benefit.reward_payload IS NULL), 0) AS user_benefit_snapshot_missing_payload_total,
       COALESCE(SUM(
           benefit.reward_payload IS NOT NULL
           AND JSON_UNQUOTE(JSON_EXTRACT(benefit.reward_payload, '$.membershipProductId')) IS NULL
       ), 0) AS user_benefit_snapshot_missing_payload_product_id_total,
       COALESCE(SUM(
           benefit.reward_payload IS NOT NULL
           AND JSON_UNQUOTE(JSON_EXTRACT(benefit.reward_payload, '$.membershipProductId')) IS NOT NULL
           AND CAST(JSON_UNQUOTE(JSON_EXTRACT(benefit.reward_payload, '$.membershipProductId')) AS UNSIGNED)
               <> benefit.reward_target_id
       ), 0) AS user_benefit_snapshot_target_payload_mismatch_total
FROM luckyhub.user_benefit benefit
LEFT JOIN luckyhub.membership_product product ON product.id = benefit.reward_target_id
WHERE benefit.reward_type = 'MEMBERSHIP';
