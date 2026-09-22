-- Repair users projected or created after V5 so authorization always has a baseline role.
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT user_record.id, user_role.id
FROM sys_user AS user_record
JOIN sys_role AS user_role
  ON user_role.role_code = 'USER'
 AND user_role.status = 1
LEFT JOIN sys_user_role AS existing_relation
  ON existing_relation.user_id = user_record.id
 AND existing_relation.role_id = user_role.id
WHERE existing_relation.user_id IS NULL;
