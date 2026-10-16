-- Remove the retired daily draw limit and RBAC tables.
ALTER TABLE marketing_activity
    DROP CHECK chk_marketing_activity_daily_limit,
    DROP COLUMN daily_limit;

DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;
