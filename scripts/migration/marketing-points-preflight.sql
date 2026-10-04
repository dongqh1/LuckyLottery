-- Read-only aggregate preflight for a future points-service extraction.
-- Confirm a pre-existing SELECT-only audit account can read luckyhub,
-- hm_item, and information_schema before running this file.
-- Review the table inventory first; stop if any required table is absent.

SELECT UTC_TIMESTAMP(3) AS audit_execution_utc;

SELECT VERSION() AS mysql_server_version;

SELECT expected.table_schema,
       expected.table_name,
       CASE WHEN actual.table_name IS NULL THEN 0 ELSE 1 END AS table_present
FROM (
    SELECT 'luckyhub' AS table_schema, 'points_account' AS table_name
    UNION ALL SELECT 'luckyhub', 'points_ledger'
    UNION ALL SELECT 'luckyhub', 'points_redemption_order'
    UNION ALL SELECT 'luckyhub', 'points_redemption_offer'
    UNION ALL SELECT 'luckyhub', 'sys_user'
    UNION ALL SELECT 'luckyhub', 'product'
    UNION ALL SELECT 'luckyhub', 'product_sku'
    UNION ALL SELECT 'luckyhub', 'shipping_address_snapshot'
    UNION ALL SELECT 'luckyhub', 'shipping_order'
    UNION ALL SELECT 'hm_item', 'item'
) expected
LEFT JOIN information_schema.tables actual
    ON actual.table_schema = expected.table_schema
    AND actual.table_name = expected.table_name
ORDER BY expected.table_schema, expected.table_name;

SELECT COUNT(*) AS points_account_total,
       COALESCE(SUM(account_record.balance = 0), 0) AS zero_balance_account_total,
       COALESCE(SUM(account_record.balance > 0), 0) AS positive_balance_account_total,
       COALESCE(SUM(account_record.balance < 0), 0) AS negative_balance_account_total,
       COALESCE(SUM(user_record.id IS NULL), 0) AS account_missing_marketing_user_total,
       (
           SELECT COUNT(*)
           FROM (
               SELECT user_id
               FROM luckyhub.points_account
               GROUP BY user_id
               HAVING COUNT(*) > 1
           ) duplicate_accounts
       ) AS duplicate_account_user_groups
FROM luckyhub.points_account account_record
LEFT JOIN luckyhub.sys_user user_record ON user_record.id = account_record.user_id;

SELECT COUNT(*) AS accounts_without_ledger_total,
       COALESCE(SUM(account_record.balance <> 0), 0) AS nonzero_accounts_without_ledger_total,
       COALESCE(SUM(
           latest_ledger.id IS NOT NULL
           AND account_record.balance <> latest_ledger.balance_after
       ), 0) AS account_latest_ledger_balance_mismatch_total
FROM luckyhub.points_account account_record
LEFT JOIN (
    SELECT ledger_user.user_id, MAX(ledger_user.id) AS latest_ledger_id
    FROM luckyhub.points_ledger ledger_user
    GROUP BY ledger_user.user_id
) latest_per_user ON latest_per_user.user_id = account_record.user_id
LEFT JOIN luckyhub.points_ledger latest_ledger
    ON latest_ledger.id = latest_per_user.latest_ledger_id;

SELECT ledger.business_type,
       ledger.direction,
       COUNT(*) AS ledger_entry_total
FROM luckyhub.points_ledger ledger
GROUP BY ledger.business_type, ledger.direction
ORDER BY ledger.business_type, ledger.direction;

SELECT COUNT(*) AS points_ledger_total,
       COALESCE(SUM(user_record.id IS NULL), 0) AS ledger_missing_marketing_user_total,
       COALESCE(SUM(account_record.user_id IS NULL), 0) AS ledger_missing_account_total,
       COALESCE(SUM(ledger.amount <= 0), 0) AS ledger_nonpositive_amount_total,
       COALESCE(SUM(ledger.balance_after < 0), 0) AS ledger_negative_balance_snapshot_total,
       (
           SELECT COUNT(*)
           FROM (
               SELECT business_type, business_id
               FROM luckyhub.points_ledger
               GROUP BY business_type, business_id
               HAVING COUNT(*) > 1
           ) duplicate_business_keys
       ) AS duplicate_ledger_business_key_groups
FROM luckyhub.points_ledger ledger
LEFT JOIN luckyhub.sys_user user_record ON user_record.id = ledger.user_id
LEFT JOIN luckyhub.points_account account_record ON account_record.user_id = ledger.user_id;

SELECT COUNT(*) AS reversal_entry_total,
       COALESCE(SUM(
           reversal.business_type = 'REVERSAL'
           AND reversal.reversal_of_ledger_id IS NULL
       ), 0) AS reversal_missing_original_total,
       COALESCE(SUM(
           reversal.reversal_of_ledger_id IS NOT NULL
           AND (
               reversal.business_type <> 'REVERSAL'
               OR reversal.direction <> 'CREDIT'
               OR original_ledger.id IS NULL
               OR original_ledger.business_type <> 'REDEMPTION'
               OR original_ledger.direction <> 'DEBIT'
               OR original_ledger.user_id <> reversal.user_id
               OR original_ledger.amount <> reversal.amount
           )
       ), 0) AS reversal_invalid_link_total,
       COALESCE(SUM(
           reversal.business_type = 'REVERSAL'
           AND reversal.reversal_of_ledger_id IS NOT NULL
           AND original_ledger.id IS NULL
       ), 0) AS reversal_orphan_original_total
FROM luckyhub.points_ledger reversal
LEFT JOIN luckyhub.points_ledger original_ledger
    ON original_ledger.id = reversal.reversal_of_ledger_id
WHERE reversal.business_type = 'REVERSAL'
   OR reversal.reversal_of_ledger_id IS NOT NULL;

SELECT COUNT(*) AS points_redemption_order_total,
       COALESCE(SUM(redemption.status = 'PROCESSING'), 0) AS processing_redemption_total,
       COALESCE(SUM(redemption.status = 'COMPLETED'), 0) AS completed_redemption_total,
       COALESCE(SUM(redemption.status = 'REVERSED'), 0) AS reversed_redemption_total,
       COALESCE(SUM(redemption.sku_id IS NOT NULL AND redemption.offer_id IS NULL), 0) AS legacy_sku_redemption_total,
       COALESCE(SUM(redemption.sku_id IS NULL AND redemption.offer_id IS NOT NULL), 0) AS hmall_offer_redemption_total,
       COALESCE(SUM(
           (redemption.sku_id IS NULL AND redemption.offer_id IS NULL)
           OR (redemption.sku_id IS NOT NULL AND redemption.offer_id IS NOT NULL)
       ), 0) AS invalid_redemption_source_total,
       COALESCE(SUM(user_record.id IS NULL), 0) AS redemption_missing_marketing_user_total,
       COALESCE(SUM(
           CAST(redemption.unit_points AS DECIMAL(65, 0)) * redemption.quantity
               <> redemption.total_points
       ), 0) AS redemption_price_calculation_mismatch_total,
       COALESCE(SUM(redemption.status = 'REVERSED' AND redemption.reversal_no IS NULL), 0)
           AS reversed_redemption_missing_reversal_key_total
FROM luckyhub.points_redemption_order redemption
LEFT JOIN luckyhub.sys_user user_record ON user_record.id = redemption.user_id;

SELECT redemption.status,
       COUNT(*) AS redemption_status_total
FROM luckyhub.points_redemption_order redemption
GROUP BY redemption.status
ORDER BY redemption.status;

SELECT redemption.product_type,
       COUNT(*) AS redemption_product_type_total
FROM luckyhub.points_redemption_order redemption
GROUP BY redemption.product_type
ORDER BY redemption.product_type;

SELECT COUNT(*) AS redemption_source_reference_total,
       COALESCE(SUM(
           redemption.sku_id IS NOT NULL AND sku_record.id IS NULL
       ), 0) AS legacy_sku_orders_missing_sku_total,
       COALESCE(SUM(
           redemption.sku_id IS NOT NULL
           AND sku_record.id IS NOT NULL
           AND product_record.id IS NULL
       ), 0) AS legacy_sku_orders_missing_product_total,
       COALESCE(SUM(
           redemption.offer_id IS NOT NULL AND offer_record.id IS NULL
       ), 0) AS hmall_offer_orders_missing_offer_total,
       COALESCE(SUM(
           redemption.offer_id IS NOT NULL
           AND offer_record.id IS NOT NULL
           AND (
               redemption.hmall_item_id IS NULL
               OR redemption.hmall_item_id <> offer_record.hmall_item_id
           )
       ), 0) AS hmall_offer_orders_offer_item_mismatch_total,
       COALESCE(SUM(
           redemption.offer_id IS NOT NULL
           AND offer_record.id IS NOT NULL
           AND item_record.id IS NULL
       ), 0) AS hmall_offer_orders_missing_item_total
FROM luckyhub.points_redemption_order redemption
LEFT JOIN luckyhub.product_sku sku_record ON sku_record.id = redemption.sku_id
LEFT JOIN luckyhub.product product_record ON product_record.id = sku_record.product_id
LEFT JOIN luckyhub.points_redemption_offer offer_record ON offer_record.id = redemption.offer_id
LEFT JOIN hm_item.item item_record ON item_record.id = offer_record.hmall_item_id;

SELECT COUNT(*) AS points_redemption_offer_total,
       COALESCE(SUM(offer.status = 1), 0) AS enabled_offer_total,
       COALESCE(SUM(offer.status = 0), 0) AS disabled_offer_total,
       COALESCE(SUM(offer.status NOT IN (0, 1) OR offer.status IS NULL), 0) AS unexpected_offer_status_total,
       COALESCE(SUM(item_record.id IS NULL), 0) AS offer_missing_hm_item_total,
       COALESCE(SUM(offer.unit_points <= 0), 0) AS offer_nonpositive_points_total,
       (
           SELECT COUNT(*)
           FROM (
               SELECT hmall_item_id
               FROM luckyhub.points_redemption_offer
               GROUP BY hmall_item_id
               HAVING COUNT(*) > 1
           ) duplicate_offer_items
       ) AS duplicate_offer_item_groups
FROM luckyhub.points_redemption_offer offer
LEFT JOIN hm_item.item item_record ON item_record.id = offer.hmall_item_id;

SELECT COUNT(*) AS completed_physical_redemption_total,
       COALESCE(SUM(redemption.address_snapshot_id IS NULL), 0)
           AS completed_physical_redemptions_missing_snapshot_total,
       COALESCE(SUM(
           redemption.address_snapshot_id IS NOT NULL
           AND snapshot_record.id IS NULL
       ), 0) AS completed_physical_redemptions_orphan_snapshot_total,
       COALESCE(SUM(
           snapshot_record.id IS NOT NULL
           AND (
               snapshot_record.source_type <> 'POINTS_REDEMPTION'
               OR snapshot_record.source_id <> CAST(redemption.id AS CHAR)
               OR snapshot_record.user_id <> redemption.user_id
           )
       ), 0) AS completed_physical_redemptions_snapshot_mismatch_total,
       COALESCE(SUM(redemption.shipping_order_id IS NULL), 0)
           AS completed_physical_redemptions_missing_shipping_total,
       COALESCE(SUM(
           redemption.shipping_order_id IS NOT NULL
           AND shipping_record.id IS NULL
       ), 0) AS completed_physical_redemptions_orphan_shipping_total,
       COALESCE(SUM(
           shipping_record.id IS NOT NULL
           AND (
               shipping_record.source_type <> 'POINTS_REDEMPTION'
               OR shipping_record.source_id <> CAST(redemption.id AS CHAR)
               OR shipping_record.target_user_id <> redemption.user_id
               OR shipping_record.address_snapshot_id <> redemption.address_snapshot_id
           )
       ), 0) AS completed_physical_redemptions_shipping_mismatch_total
FROM luckyhub.points_redemption_order redemption
LEFT JOIN luckyhub.shipping_address_snapshot snapshot_record
    ON snapshot_record.id = redemption.address_snapshot_id
LEFT JOIN luckyhub.shipping_order shipping_record
    ON shipping_record.id = redemption.shipping_order_id
WHERE redemption.status = 'COMPLETED'
  AND redemption.product_type = 'PHYSICAL';

SELECT COUNT(*) AS settled_redemption_total,
       COALESCE(SUM(
           redemption.status IN ('COMPLETED', 'REVERSED')
           AND (
               debit_ledger.id IS NULL
               OR debit_ledger.user_id <> redemption.user_id
               OR debit_ledger.direction <> 'DEBIT'
               OR debit_ledger.amount <> redemption.total_points
           )
       ), 0) AS completed_redemptions_missing_debit_ledger_total,
       COALESCE(SUM(
           redemption.status = 'REVERSED'
           AND (
               reversal_ledger.id IS NULL
               OR debit_ledger.id IS NULL
               OR reversal_ledger.user_id <> redemption.user_id
               OR reversal_ledger.direction <> 'CREDIT'
               OR reversal_ledger.reversal_of_ledger_id <> debit_ledger.id
               OR reversal_ledger.amount <> debit_ledger.amount
           )
       ), 0) AS reversed_redemptions_missing_reversal_ledger_total
FROM luckyhub.points_redemption_order redemption
LEFT JOIN luckyhub.points_ledger debit_ledger
    ON debit_ledger.business_type = 'REDEMPTION'
    AND debit_ledger.business_id = redemption.redemption_no
LEFT JOIN luckyhub.points_ledger reversal_ledger
    ON reversal_ledger.business_type = 'REVERSAL'
    AND reversal_ledger.business_id = redemption.reversal_no;

SELECT COUNT(*) AS redemption_ledger_entry_total,
       COALESCE(SUM(redemption_record.id IS NULL), 0) AS debit_ledger_missing_redemption_total,
       COALESCE(SUM(
           redemption_record.id IS NOT NULL
           AND (
               redemption_record.user_id <> ledger.user_id
               OR redemption_record.total_points <> ledger.amount
           )
       ), 0) AS debit_ledger_redemption_mismatch_total
FROM luckyhub.points_ledger ledger
LEFT JOIN luckyhub.points_redemption_order redemption_record
    ON redemption_record.redemption_no = ledger.business_id
WHERE ledger.business_type = 'REDEMPTION';
