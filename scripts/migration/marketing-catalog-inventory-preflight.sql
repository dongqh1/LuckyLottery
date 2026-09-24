-- Product and SKU status distribution.
SELECT 'product_status' AS metric, CAST(status AS CHAR) AS dimension, COUNT(*) AS row_count
FROM luckyhub.product
GROUP BY status
UNION ALL
SELECT 'product_sku_status', CAST(status AS CHAR), COUNT(*)
FROM luckyhub.product_sku
GROUP BY status;

-- Whether product_sku contains an explicit HMall item reference; do not infer one by matching text.
SELECT 'product_sku_direct_item_link_columns' AS metric,
       COUNT(*) AS aggregate_count
FROM information_schema.columns
WHERE table_schema = 'luckyhub'
  AND table_name = 'product_sku'
  AND column_name IN ('item_id', 'hmall_item_id');

-- Catalog sales-mode distribution, without returning price or SKU values.
SELECT 'product_sku_sale_mode' AS metric,
       CONCAT(cash_enabled, ':', points_enabled) AS dimension,
       COUNT(*) AS row_count
FROM luckyhub.product_sku
GROUP BY cash_enabled, points_enabled;

-- Ledger identity shape and aggregate orphan counts.
SELECT 'inventory_ledger_identity_shape' AS metric,
       CASE
           WHEN sku_id IS NOT NULL AND hmall_item_id IS NULL THEN 'marketing_sku_only'
           WHEN sku_id IS NULL AND hmall_item_id IS NOT NULL THEN 'hmall_item_only'
           WHEN sku_id IS NULL AND hmall_item_id IS NULL THEN 'both_null'
           ELSE 'both_present'
       END AS dimension,
       COUNT(*) AS row_count
FROM luckyhub.inventory_ledger
GROUP BY dimension;

SELECT 'inventory_ledger_missing_marketing_sku' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.inventory_ledger ledger
LEFT JOIN luckyhub.product_sku sku ON sku.id = ledger.sku_id
WHERE ledger.sku_id IS NOT NULL AND sku.id IS NULL;

SELECT 'inventory_ledger_missing_hmall_item' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.inventory_ledger ledger
LEFT JOIN hmall.item item ON item.id = ledger.hmall_item_id
WHERE ledger.hmall_item_id IS NOT NULL AND item.id IS NULL;

-- Reservation states and stored stock invariant anomalies.
SELECT 'inventory_reservation_status' AS metric, status AS dimension, COUNT(*) AS row_count
FROM luckyhub.inventory_reservation
GROUP BY status;

SELECT 'sku_inventory_allocation_exceeds_total' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.sku_inventory
WHERE allocated_stock > total_stock;

SELECT 'channel_stock_balance_mismatch' AS metric, COUNT(*) AS aggregate_count
FROM luckyhub.inventory_channel_stock
WHERE allocated_stock <> available_stock + reserved_stock + consumed_stock;

-- Target item population is an aggregate only; it does not assert a Marketing SKU mapping.
SELECT 'hmall_item_total' AS metric, COUNT(*) AS aggregate_count
FROM hmall.item;
