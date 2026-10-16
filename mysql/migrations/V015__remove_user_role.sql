-- Retire legacy user roles. Login continues to use the user id only.
USE `hmall`;

SET @drop_hmall_user_role_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'hmall' AND table_name = 'user' AND column_name = 'role'
    ),
    'ALTER TABLE `user` DROP COLUMN `role`',
    'SELECT 1'
);
PREPARE drop_hmall_user_role_stmt FROM @drop_hmall_user_role_sql;
EXECUTE drop_hmall_user_role_stmt;
DEALLOCATE PREPARE drop_hmall_user_role_stmt;

USE `hm_user`;

SET @drop_hm_user_role_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'hm_user' AND table_name = 'user' AND column_name = 'role'
    ),
    'ALTER TABLE `user` DROP COLUMN `role`',
    'SELECT 1'
);
PREPARE drop_hm_user_role_stmt FROM @drop_hm_user_role_sql;
EXECUTE drop_hm_user_role_stmt;
DEALLOCATE PREPARE drop_hm_user_role_stmt;
