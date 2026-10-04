-- One-time, idempotent cutover from the retired luckyhub marketing schema.
-- User-owned rows are copied only when the legacy hmall:{id} projection has
-- a matching user in hm_user. This deliberately excludes orphan projections.

USE hm_lottery;

START TRANSACTION;

CREATE TEMPORARY TABLE verified_hmall_user (
    id BIGINT UNSIGNED NOT NULL PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO verified_hmall_user (id)
SELECT legacy_user.id
FROM luckyhub.sys_user legacy_user
JOIN hm_user.`user` hmall_user ON hmall_user.id = legacy_user.id
WHERE legacy_user.username = CONCAT('hmall:', legacy_user.id);

-- Coupon domain
INSERT IGNORE INTO hm_coupon.coupon_template
SELECT * FROM luckyhub.coupon_template;

INSERT IGNORE INTO hm_coupon.user_coupon
SELECT source_row.*
FROM luckyhub.user_coupon source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id;

INSERT IGNORE INTO hm_coupon.coupon_issue_record
SELECT source_row.*
FROM luckyhub.coupon_issue_record source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id
JOIN hm_coupon.user_coupon migrated_coupon ON migrated_coupon.id = source_row.user_coupon_id;

-- Membership domain
INSERT IGNORE INTO hm_membership.membership_product
SELECT * FROM luckyhub.membership_product;

INSERT IGNORE INTO hm_membership.user_membership
SELECT source_row.*
FROM luckyhub.user_membership source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id;

INSERT IGNORE INTO hm_membership.membership_grant_record
SELECT source_row.*
FROM luckyhub.membership_grant_record source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id;

-- Points domain. The legacy database currently has no redemption orders; its
-- old order shape is intentionally not inserted into the newer workflow table.
INSERT IGNORE INTO hm_points.points_redemption_offer
SELECT * FROM luckyhub.points_redemption_offer;

INSERT IGNORE INTO hm_points.points_account
SELECT source_row.*
FROM luckyhub.points_account source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id;

INSERT IGNORE INTO hm_points.points_ledger
SELECT source_row.*
FROM luckyhub.points_ledger source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id;

-- Lottery catalog
INSERT IGNORE INTO hm_lottery.reward_definition
SELECT * FROM luckyhub.reward_definition;

INSERT IGNORE INTO hm_lottery.marketing_prize
SELECT * FROM luckyhub.marketing_prize;

INSERT IGNORE INTO hm_lottery.marketing_activity
SELECT * FROM luckyhub.marketing_activity;

INSERT IGNORE INTO hm_lottery.marketing_activity_prize
SELECT * FROM luckyhub.marketing_activity_prize;

-- Lottery user state and history
INSERT IGNORE INTO hm_lottery.draw_chance_account
SELECT source_row.*
FROM luckyhub.draw_chance_account source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id;

INSERT IGNORE INTO hm_lottery.draw_chance_ledger
SELECT source_row.*
FROM luckyhub.draw_chance_ledger source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id;

INSERT IGNORE INTO hm_lottery.draw_chance_reservation
SELECT source_row.*
FROM luckyhub.draw_chance_reservation source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id;

INSERT IGNORE INTO hm_lottery.lottery_draw_order
SELECT source_row.*
FROM luckyhub.lottery_draw_order source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id;

INSERT IGNORE INTO hm_lottery.lottery_draw_record
SELECT source_row.*
FROM luckyhub.lottery_draw_record source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id
JOIN hm_lottery.lottery_draw_order migrated_order ON migrated_order.id = source_row.order_id;

INSERT IGNORE INTO hm_lottery.user_benefit
SELECT source_row.*
FROM luckyhub.user_benefit source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id
JOIN hm_lottery.lottery_draw_record migrated_record ON migrated_record.id = source_row.draw_record_id;

INSERT IGNORE INTO hm_lottery.lottery_reward_quarantine
SELECT source_row.*
FROM luckyhub.lottery_reward_quarantine source_row
WHERE (source_row.order_id IS NULL OR EXISTS (
         SELECT 1 FROM hm_lottery.lottery_draw_order migrated_order
         WHERE migrated_order.id = source_row.order_id
      ))
  AND (source_row.draw_record_id IS NULL OR EXISTS (
         SELECT 1 FROM hm_lottery.lottery_draw_record migrated_record
         WHERE migrated_record.id = source_row.draw_record_id
      ))
  AND (source_row.benefit_id IS NULL OR EXISTS (
         SELECT 1 FROM hm_lottery.user_benefit migrated_benefit
         WHERE migrated_benefit.id = source_row.benefit_id
      ));

-- Preserve completed messaging history without replaying pending legacy work.
INSERT IGNORE INTO hm_lottery.message_outbox
SELECT source_row.*
FROM luckyhub.message_outbox source_row
WHERE source_row.status IN ('SENT', 'FAILED');

INSERT IGNORE INTO hm_lottery.message_consume_record
SELECT * FROM luckyhub.message_consume_record;

-- Fulfillment domain
INSERT IGNORE INTO hm_fulfillment.fulfillment_task
SELECT source_row.*
FROM luckyhub.fulfillment_task source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.target_user_id;

INSERT IGNORE INTO hm_fulfillment.fulfillment_attempt
SELECT source_row.*
FROM luckyhub.fulfillment_attempt source_row
JOIN hm_fulfillment.fulfillment_task migrated_task ON migrated_task.id = source_row.task_id;

INSERT IGNORE INTO hm_fulfillment.fulfillment_quarantine
SELECT source_row.*
FROM luckyhub.fulfillment_quarantine source_row
JOIN hm_fulfillment.fulfillment_task migrated_task ON migrated_task.id = source_row.task_id;

INSERT IGNORE INTO hm_fulfillment.fulfillment_outbox
SELECT source_row.*
FROM luckyhub.fulfillment_outbox source_row
JOIN hm_fulfillment.fulfillment_task migrated_task
  ON migrated_task.fulfillment_no = source_row.fulfillment_no
WHERE source_row.status IN ('SENT', 'FAILED');

INSERT IGNORE INTO hm_fulfillment.shipping_address_snapshot
SELECT source_row.*
FROM luckyhub.shipping_address_snapshot source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.user_id;

INSERT IGNORE INTO hm_fulfillment.shipping_order
SELECT source_row.*
FROM luckyhub.shipping_order source_row
JOIN verified_hmall_user verified_user ON verified_user.id = source_row.target_user_id
JOIN hm_fulfillment.shipping_address_snapshot migrated_snapshot
  ON migrated_snapshot.id = source_row.address_snapshot_id;

INSERT IGNORE INTO hm_fulfillment.shipping_tracking_event
SELECT source_row.*
FROM luckyhub.shipping_tracking_event source_row
JOIN hm_fulfillment.shipping_order migrated_order
  ON migrated_order.id = source_row.shipping_order_id;

INSERT IGNORE INTO hm_fulfillment.shipping_callback_receipt
SELECT source_row.*
FROM luckyhub.shipping_callback_receipt source_row
WHERE EXISTS (
    SELECT 1
    FROM hm_fulfillment.shipping_order migrated_order
    WHERE migrated_order.waybill_no = source_row.waybill_no
);

INSERT IGNORE INTO hm_fulfillment.sim_logistics_record
SELECT source_row.*
FROM luckyhub.sim_logistics_record source_row
JOIN hm_fulfillment.shipping_order migrated_order
  ON migrated_order.fulfillment_no = source_row.fulfillment_no;

DROP TEMPORARY TABLE verified_hmall_user;

COMMIT;
