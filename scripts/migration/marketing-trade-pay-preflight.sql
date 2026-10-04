-- Read-only aggregate preflight. Run only with an already-confirmed SELECT-only account.
-- Do not retain row-level client output; this script emits only version, schema inventory,
-- status/amount aggregates, and aggregate mismatch counts.

SELECT VERSION() AS mysql_server_version;

SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema IN ('luckyhub', 'hm_trade', 'hm_pay')
  AND table_name IN (
    'mall_order', 'payment_order', 'order', 'pay_order', 'pay_message_outbox'
  )
ORDER BY table_schema, table_name;

SELECT COUNT(*) AS marketing_order_total
FROM luckyhub.mall_order;

SELECT status, COUNT(*) AS row_count
FROM luckyhub.mall_order
GROUP BY status
ORDER BY status;

SELECT COUNT(*) AS marketing_payment_total,
       COALESCE(SUM(amount_cent), 0) AS amount_cent_sum,
       MIN(amount_cent) AS amount_cent_min,
       MAX(amount_cent) AS amount_cent_max
FROM luckyhub.payment_order;

SELECT status, callback_result, COUNT(*) AS row_count,
       COALESCE(SUM(amount_cent), 0) AS amount_cent_sum
FROM luckyhub.payment_order
GROUP BY status, callback_result
ORDER BY status, callback_result;

SELECT COUNT(*) AS orders_with_multiple_payment_attempts,
       COALESCE(MAX(payment_attempt_count), 0) AS max_payment_attempts_for_one_order
FROM (
    SELECT COUNT(*) AS payment_attempt_count
    FROM luckyhub.payment_order
    GROUP BY order_no
    HAVING COUNT(*) > 1
) attempts;

SELECT (SELECT COUNT(*)
        FROM luckyhub.payment_order p
        LEFT JOIN luckyhub.mall_order o ON o.order_no = p.order_no
        WHERE o.id IS NULL) AS orphan_payment_count,
       (SELECT COUNT(*)
        FROM luckyhub.mall_order o
        LEFT JOIN luckyhub.payment_order p ON p.order_no = o.order_no
        WHERE p.id IS NULL) AS orders_without_payment_count,
       (SELECT COUNT(*)
        FROM luckyhub.payment_order p
        JOIN luckyhub.mall_order o ON o.order_no = p.order_no
        WHERE p.amount_cent <> o.payable_amount_cent) AS payment_amount_mismatch_count;

SELECT COUNT(*) AS trade_order_total,
       MIN(total_fee) AS total_fee_min,
       MAX(total_fee) AS total_fee_max
FROM hm_trade.`order`;

SELECT status, COUNT(*) AS row_count,
       COALESCE(SUM(total_fee), 0) AS total_fee_sum
FROM hm_trade.`order`
GROUP BY status
ORDER BY status;

SELECT COUNT(*) AS pay_order_total,
       MIN(amount) AS amount_min,
       MAX(amount) AS amount_max
FROM hm_pay.pay_order;

SELECT status, COUNT(*) AS row_count,
       COALESCE(SUM(amount), 0) AS amount_sum
FROM hm_pay.pay_order
GROUP BY status
ORDER BY status;

SELECT event_type, status, COUNT(*) AS row_count
FROM hm_pay.pay_message_outbox
GROUP BY event_type, status
ORDER BY event_type, status;
