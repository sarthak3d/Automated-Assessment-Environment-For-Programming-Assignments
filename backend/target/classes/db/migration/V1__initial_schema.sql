-- Flyway Migration: Create initial schema for Assessment Platform

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    sso_id VARCHAR(255) UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    department VARCHAR(100),
    student_id VARCHAR(50),
    gitlab_user_id BIGINT,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_sso_id ON users(sso_id);
CREATE INDEX idx_users_role ON users(role);

CREATE TABLE courses (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    instructor_id UUID NOT NULL REFERENCES users(id),
    gitlab_group_id BIGINT,
    semester VARCHAR(20) NOT NULL,
    year INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_courses_code ON courses(code);
CREATE INDEX idx_courses_instructor ON courses(instructor_id);

CREATE TABLE course_enrollments (
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (course_id, user_id)
);

CREATE INDEX idx_enrollments_course ON course_enrollments(course_id);
CREATE INDEX idx_enrollments_user ON course_enrollments(user_id);

CREATE TABLE test_modules (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    docker_image VARCHAR(255) NOT NULL,
    docker_tag VARCHAR(100),
    output_type VARCHAR(30) NOT NULL,
    use_for_grading BOOLEAN NOT NULL DEFAULT true,
    predefined BOOLEAN NOT NULL DEFAULT true,
    min_value DOUBLE PRECISION,
    max_value DOUBLE PRECISION,
    default_timeout_seconds INTEGER NOT NULL DEFAULT 300,
    default_memory_limit_mb INTEGER NOT NULL DEFAULT 2048,
    config_schema TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_test_modules_name ON test_modules(name);
CREATE INDEX idx_test_modules_output_type ON test_modules(output_type);

CREATE TABLE test_module_valid_texts (
    test_module_id UUID NOT NULL REFERENCES test_modules(id) ON DELETE CASCADE,
    valid_text VARCHAR(255) NOT NULL
);

CREATE TABLE assignments (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    gitlab_project_id BIGINT,
    gitlab_project_path VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    due_date TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    max_submissions INTEGER NOT NULL DEFAULT 10,
    allow_late_submissions BOOLEAN NOT NULL DEFAULT false,
    ci_config_yaml TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_assignments_course ON assignments(course_id);
CREATE INDEX idx_assignments_due_date ON assignments(due_date);
CREATE INDEX idx_assignments_status ON assignments(status);

CREATE TABLE assignment_test_modules (
    assignment_id UUID NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    test_module_id UUID NOT NULL REFERENCES test_modules(id),
    PRIMARY KEY (assignment_id, test_module_id)
);

CREATE TABLE test_module_weights (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    test_module_id UUID NOT NULL REFERENCES test_modules(id),
    weight DOUBLE PRECISION NOT NULL,
    order_index INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT true,
    custom_config TEXT,
    custom_timeout_seconds INTEGER,
    custom_memory_limit_mb INTEGER
);

CREATE INDEX idx_weights_assignment ON test_module_weights(assignment_id);
CREATE INDEX idx_weights_module ON test_module_weights(test_module_id);

CREATE TABLE questions (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    markdown_content TEXT NOT NULL,
    markdown_file_path VARCHAR(500),
    assignment_id UUID NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    language_id INTEGER NOT NULL,
    language_name VARCHAR(50) NOT NULL,
    order_index INTEGER NOT NULL DEFAULT 0,
    points INTEGER NOT NULL DEFAULT 100,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    memory_limit_mb INTEGER NOT NULL DEFAULT 256,
    starter_code TEXT,
    solution_code TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_questions_assignment ON questions(assignment_id);

CREATE TABLE question_stdin (
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    stdin_input TEXT NOT NULL,
    input_order INTEGER NOT NULL
);

CREATE TABLE question_expected_output (
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    expected_output TEXT NOT NULL,
    output_order INTEGER NOT NULL
);

CREATE TABLE submissions (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES users(id),
    assignment_id UUID NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    commit_sha VARCHAR(100),
    pipeline_id BIGINT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    pipeline_logs TEXT,
    pipeline_started_at TIMESTAMP WITH TIME ZONE,
    pipeline_finished_at TIMESTAMP WITH TIME ZONE,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_submissions_student ON submissions(student_id);
CREATE INDEX idx_submissions_assignment ON submissions(assignment_id);
CREATE INDEX idx_submissions_pipeline ON submissions(pipeline_id);
CREATE INDEX idx_submissions_status ON submissions(status);

CREATE TABLE test_results (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    test_module_id UUID NOT NULL REFERENCES test_modules(id),
    job_id BIGINT,
    job_name VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    raw_output TEXT,
    pass_result BOOLEAN,
    numeric_result DOUBLE PRECISION,
    text_result VARCHAR(255),
    percentage_result DOUBLE PRECISION,
    normalized_score DOUBLE PRECISION,
    suggestion_text TEXT,
    error_message TEXT,
    execution_time_ms BIGINT,
    memory_used_bytes BIGINT,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_test_results_submission ON test_results(submission_id);
CREATE INDEX idx_test_results_module ON test_results(test_module_id);
CREATE INDEX idx_test_results_job ON test_results(job_id);

CREATE TABLE grades (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL UNIQUE REFERENCES submissions(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES users(id),
    assignment_id UUID NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    total_score DOUBLE PRECISION NOT NULL,
    max_possible_score DOUBLE PRECISION NOT NULL,
    percentage_score DOUBLE PRECISION NOT NULL,
    letter_grade VARCHAR(10),
    auto_graded BOOLEAN NOT NULL DEFAULT true,
    score_breakdown TEXT,
    feedback TEXT,
    graded_by UUID REFERENCES users(id),
    manually_graded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_grades_submission ON grades(submission_id);
CREATE INDEX idx_grades_student_assignment ON grades(student_id, assignment_id);
