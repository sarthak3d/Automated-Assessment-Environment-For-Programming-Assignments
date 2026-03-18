ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

INSERT INTO users (id, username, email, sso_id, first_name, last_name, role, active, department, password_hash, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'admin',
    'admin@assessment.local',
    'dev-admin-sso',
    'Admin',
    'User',
    'ADMIN',
    true,
    'Administration',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    NOW(),
    NOW()
) ON CONFLICT (username) DO NOTHING;

INSERT INTO users (id, username, email, sso_id, first_name, last_name, role, active, department, password_hash, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000002',
    'teacher',
    'teacher@assessment.local',
    'dev-teacher-sso',
    'Jane',
    'Instructor',
    'TEACHER',
    true,
    'Computer Science',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    NOW(),
    NOW()
) ON CONFLICT (username) DO NOTHING;

INSERT INTO users (id, username, email, sso_id, first_name, last_name, role, active, department, student_id, password_hash, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000003',
    'student',
    'student@assessment.local',
    'dev-student-sso',
    'John',
    'Learner',
    'STUDENT',
    true,
    'Computer Science',
    'STU-001',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    NOW(),
    NOW()
) ON CONFLICT (username) DO NOTHING;
