ALTER TABLE reward_definition
    DROP COLUMN config_snapshot;

ALTER TABLE lottery_draw_record
    DROP CHECK chk_draw_record_reward_type,
    DROP CHECK chk_draw_record_reward_quantity,
    DROP INDEX idx_draw_record_reward_definition,
    DROP COLUMN prize_name,
    DROP COLUMN prize_type,
    DROP COLUMN prize_image_url,
    DROP COLUMN reward_definition_id,
    DROP COLUMN reward_type,
    DROP COLUMN reward_target_id,
    DROP COLUMN reward_quantity,
    DROP COLUMN reward_payload,
    DROP COLUMN reward_fingerprint;

ALTER TABLE user_benefit
    DROP CHECK chk_user_benefit_reward_type,
    DROP CHECK chk_user_benefit_reward_quantity,
    DROP INDEX idx_user_benefit_reward_definition,
    DROP COLUMN prize_type,
    DROP COLUMN reward_definition_id,
    DROP COLUMN reward_type,
    DROP COLUMN reward_target_id,
    DROP COLUMN reward_quantity,
    DROP COLUMN reward_payload,
    DROP COLUMN reward_fingerprint;

UPDATE message_outbox
SET payload = JSON_REMOVE(
    payload,
    '$.payload.prizeType',
    '$.payload.rewardDefinitionId',
    '$.payload.rewardType',
    '$.payload.rewardFingerprint'
)
WHERE event_type = 'PRIZE_FULFILLMENT_REQUESTED';
