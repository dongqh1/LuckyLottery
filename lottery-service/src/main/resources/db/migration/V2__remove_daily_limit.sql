ALTER TABLE marketing_activity
    DROP CHECK chk_marketing_activity_daily_limit,
    DROP COLUMN daily_limit;
