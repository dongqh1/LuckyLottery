ALTER TABLE user_benefit
    DROP INDEX uk_user_benefit_fulfillment,
    DROP COLUMN fulfillment_no;
