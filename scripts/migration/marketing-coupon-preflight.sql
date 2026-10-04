-- Read-only aggregate preflight for coupon ownership and references.
-- Execute only with an already-confirmed SELECT-only account that can read
-- luckyhub, hmall, and information_schema. Do not export row-level results.
SELECT VERSION() AS mysql_server_version;

-- Required source tables present in luckyhub; names are fixed by this script.
SELECT 'required_luckyhub_coupon_tables_found' AS metric,
       COUNT(DISTINCT table_name) AS aggregate_count
FROM information_schema.tables
WHERE table_schema = 'luckyhub'
  AND table_name IN (
      'coupon_template',
      'user_coupon',
      'coupon_issue_record',
      'mall_order',
      'sys_user'
  );

-- Coupon templates: only categorical status/type/scope dimensions.
SELECT 'coupon_template_total' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.coupon_template;

SELECT 'coupon_template_status' AS metric,
       CAST(status AS CHAR) AS dimension,
       COUNT(*) AS row_count
FROM luckyhub.coupon_template
GROUP BY status;

SELECT 'coupon_template_scope_distribution' AS metric,
       COALESCE(scope_type, '<NULL_OR_LEGACY>') AS dimension,
       COUNT(*) AS row_count
FROM luckyhub.coupon_template
GROUP BY scope_type;

SELECT 'coupon_template_type_distribution' AS metric,
       coupon_type AS dimension,
       COUNT(*) AS row_count
FROM luckyhub.coupon_template
GROUP BY coupon_type;

SELECT 'user_coupon_status' AS metric,
       status AS dimension,
       COUNT(*) AS row_count
FROM luckyhub.user_coupon
GROUP BY status;

SELECT 'user_coupon_total' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.user_coupon;

SELECT 'coupon_issue_record_total' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.coupon_issue_record;

-- User, template, issue-record and order references; only aggregate counts leave the database.
SELECT 'user_coupon_missing_template' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.user_coupon coupon
LEFT JOIN luckyhub.coupon_template template ON template.id = coupon.template_id
WHERE template.id IS NULL;

SELECT 'user_coupon_missing_marketing_user' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.user_coupon coupon
LEFT JOIN luckyhub.sys_user account ON account.id = coupon.user_id
WHERE account.id IS NULL;

SELECT 'coupon_issue_missing_template' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.coupon_issue_record issue_record
LEFT JOIN luckyhub.coupon_template template ON template.id = issue_record.template_id
WHERE template.id IS NULL;

SELECT 'coupon_issue_missing_user_coupon' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.coupon_issue_record issue_record
LEFT JOIN luckyhub.user_coupon coupon ON coupon.id = issue_record.user_coupon_id
WHERE coupon.id IS NULL;

SELECT 'coupon_issue_missing_marketing_user' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.coupon_issue_record issue_record
LEFT JOIN luckyhub.sys_user account ON account.id = issue_record.user_id
WHERE account.id IS NULL;

SELECT 'coupon_issue_mapping_mismatch' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.coupon_issue_record issue_record
JOIN luckyhub.user_coupon coupon ON coupon.id = issue_record.user_coupon_id
WHERE issue_record.template_id <> coupon.template_id
   OR issue_record.user_id <> coupon.user_id;

-- Return the number of duplicate idempotency-key groups, never the key values.
SELECT 'duplicate_coupon_issue_business_key_groups' AS metric,
       COUNT(*) AS aggregate_count
FROM (
    SELECT business_no
    FROM luckyhub.coupon_issue_record
    GROUP BY business_no
    HAVING COUNT(*) > 1
) duplicate_issue_keys;

SELECT 'coupon_applied_order_status' AS metric,
       status AS dimension,
       COUNT(*) AS row_count
FROM luckyhub.mall_order
WHERE user_coupon_id IS NOT NULL OR coupon_template_id IS NOT NULL
GROUP BY status;

SELECT 'mall_order_missing_user_coupon' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.mall_order orders
LEFT JOIN luckyhub.user_coupon coupon ON coupon.id = orders.user_coupon_id
WHERE orders.user_coupon_id IS NOT NULL
  AND coupon.id IS NULL;

SELECT 'mall_order_missing_coupon_template' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.mall_order orders
LEFT JOIN luckyhub.coupon_template template ON template.id = orders.coupon_template_id
WHERE orders.coupon_template_id IS NOT NULL
  AND template.id IS NULL;

SELECT 'mall_order_partial_coupon_reference' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.mall_order
WHERE (user_coupon_id IS NULL AND coupon_template_id IS NOT NULL)
   OR (user_coupon_id IS NOT NULL AND coupon_template_id IS NULL);

SELECT 'mall_order_coupon_mapping_mismatch' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.mall_order orders
JOIN luckyhub.user_coupon coupon ON coupon.id = orders.user_coupon_id
WHERE orders.user_coupon_id IS NOT NULL
  AND (orders.user_id <> coupon.user_id
       OR orders.coupon_template_id <> coupon.template_id);

SELECT 'coupon_template_scope_field_anomalies' AS metric,
       COALESCE(SUM(CASE WHEN scope_type IS NULL THEN 1 ELSE 0 END), 0) AS null_scope_count,
       COALESCE(SUM(CASE WHEN scope_type = 'ITEM' AND hmall_item_id IS NULL THEN 1 ELSE 0 END), 0) AS item_scope_missing_item_id,
       COALESCE(SUM(CASE WHEN scope_type = 'CATEGORY' AND (hmall_category IS NULL OR TRIM(hmall_category) = '') THEN 1 ELSE 0 END), 0) AS category_scope_missing_category,
       COALESCE(SUM(CASE WHEN scope_type = 'BRAND' AND (hmall_brand IS NULL OR TRIM(hmall_brand) = '') THEN 1 ELSE 0 END), 0) AS brand_scope_missing_brand,
       COALESCE(SUM(CASE WHEN hmall_item_id IS NOT NULL AND (scope_type IS NULL OR scope_type <> 'ITEM') THEN 1 ELSE 0 END), 0) AS item_id_outside_item_scope
FROM luckyhub.coupon_template;

SELECT 'coupon_template_missing_hmall_item' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.coupon_template template
LEFT JOIN hmall.item item ON item.id = template.hmall_item_id
WHERE template.scope_type = 'ITEM'
  AND template.hmall_item_id IS NOT NULL
  AND item.id IS NULL;

-- Legacy Marketing product references are checked only against the source product table.
SELECT 'coupon_template_legacy_product_missing' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.coupon_template template
LEFT JOIN luckyhub.product product ON product.id = template.applicable_product_id
WHERE template.applicable_product_id IS NOT NULL
  AND product.id IS NULL;
