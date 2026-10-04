-- Marketing identity and RBAC preflight audit.
-- Run with a MySQL account that has SELECT access only.
-- Detailed identity rows are transient review output and must not be committed.

-- Report the server version; mysql.exe --version reports only the client version.
SELECT VERSION() AS mysql_server_version;

-- User totals in both identity databases.
SELECT 'luckyhub.sys_user' AS identity_source, COUNT(*) AS user_count
  FROM luckyhub.sys_user
UNION ALL
SELECT 'hm_user.user' AS identity_source, COUNT(*) AS user_count
  FROM hm_user.user;

-- Status distributions. The two databases use different status encodings.
SELECT 'luckyhub.sys_user' AS identity_source, status, COUNT(*) AS user_count
  FROM luckyhub.sys_user
 GROUP BY status
UNION ALL
SELECT 'hm_user.user' AS identity_source, status, COUNT(*) AS user_count
  FROM hm_user.user
 GROUP BY status
 ORDER BY identity_source, status;

-- Marketing password format distribution only; no password value is returned.
SELECT CASE
           WHEN password REGEXP '^\\$2[aby]\\$[0-9]{2}\\$' THEN 'bcrypt'
           WHEN password = '!HMALL_EXTERNAL_IDENTITY!' THEN 'external_identity_marker'
           WHEN password IS NULL OR password = '' THEN 'empty'
           ELSE 'other'
       END AS password_format,
       COUNT(*) AS user_count
  FROM luckyhub.sys_user
 GROUP BY password_format
 ORDER BY password_format;

-- Aggregate identity conflict counts for the sanitized audit report.
SELECT 'same_id_different_username' AS conflict_type, COUNT(*) AS conflict_count
  FROM luckyhub.sys_user AS m
  JOIN hm_user.user AS h ON h.id = m.id
 WHERE m.username NOT LIKE CONCAT('hmall:', h.id)
   AND m.username <> h.username
UNION ALL
SELECT 'same_username_different_id' AS conflict_type, COUNT(*) AS conflict_count
  FROM luckyhub.sys_user AS m
  JOIN hm_user.user AS h ON h.username = m.username
 WHERE h.id <> m.id
UNION ALL
SELECT 'hmall_projection_missing_or_mismatched' AS conflict_type,
       COUNT(*) AS conflict_count
  FROM luckyhub.sys_user AS m
  LEFT JOIN hm_user.user AS h ON h.id = m.id
 WHERE m.username LIKE 'hmall:%'
   AND (h.id IS NULL OR m.username <> CONCAT('hmall:', m.id));

-- Detailed same-ID conflicts for controlled review only.
SELECT m.id AS marketing_user_id, m.username AS marketing_username,
       h.id AS hmall_user_id, h.username AS hmall_username
  FROM luckyhub.sys_user AS m
  JOIN hm_user.user AS h ON h.id = m.id
 WHERE m.username NOT LIKE CONCAT('hmall:', h.id)
   AND m.username <> h.username
 ORDER BY m.id;

-- Detailed same-username conflicts for controlled review only.
SELECT m.id AS marketing_user_id, m.username AS marketing_username,
       h.id AS hmall_user_id
  FROM luckyhub.sys_user AS m
  JOIN hm_user.user AS h ON h.username = m.username
 WHERE h.id <> m.id
 ORDER BY m.username, m.id;

-- Marketing hmall:{id} projections must have the same ID in hm_user.user.
SELECT m.id AS marketing_user_id, m.username AS marketing_username,
       h.id AS hmall_user_id
  FROM luckyhub.sys_user AS m
  LEFT JOIN hm_user.user AS h ON h.id = m.id
 WHERE m.username LIKE 'hmall:%'
   AND (h.id IS NULL OR m.username <> CONCAT('hmall:', m.id))
 ORDER BY m.id;

-- Aggregate orphan counts for all RBAC relationships.
SELECT 'sys_user_role.user_id' AS relationship_type,
       COUNT(*) AS orphan_count
  FROM luckyhub.sys_user_role AS ur
  LEFT JOIN luckyhub.sys_user AS u ON u.id = ur.user_id
 WHERE u.id IS NULL
UNION ALL
SELECT 'sys_user_role.role_id' AS relationship_type,
       COUNT(*) AS orphan_count
  FROM luckyhub.sys_user_role AS ur
  LEFT JOIN luckyhub.sys_role AS r ON r.id = ur.role_id
 WHERE r.id IS NULL
UNION ALL
SELECT 'sys_role_permission.role_id' AS relationship_type,
       COUNT(*) AS orphan_count
  FROM luckyhub.sys_role_permission AS rp
  LEFT JOIN luckyhub.sys_role AS r ON r.id = rp.role_id
 WHERE r.id IS NULL
UNION ALL
SELECT 'sys_role_permission.permission_id' AS relationship_type,
       COUNT(*) AS orphan_count
  FROM luckyhub.sys_role_permission AS rp
  LEFT JOIN luckyhub.sys_permission AS p ON p.id = rp.permission_id
 WHERE p.id IS NULL;

-- Structural inventory only; this does not validate row-level user mappings.
SELECT table_name, column_name
  FROM information_schema.columns
 WHERE table_schema = 'luckyhub'
   AND column_name = 'user_id'
 ORDER BY table_name;
