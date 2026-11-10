INSERT INTO sys_user (
    username, password, real_name, email, status, create_time, update_time
) VALUES (
    'system_night_audit',
    '!non-login-system-actor!',
    'SYSTEM Night Audit',
    'system-night-audit@localhost.invalid',
    1,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'MANAGER'
WHERE u.username = 'system_night_audit';
