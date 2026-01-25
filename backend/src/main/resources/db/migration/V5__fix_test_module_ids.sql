-- Flyway Migration: Fix test module IDs to be deterministic

-- First, drop and recreate foreign keys with ON UPDATE CASCADE
ALTER TABLE test_module_valid_texts DROP CONSTRAINT IF EXISTS test_module_valid_texts_test_module_id_fkey;
ALTER TABLE test_module_valid_texts ADD CONSTRAINT test_module_valid_texts_test_module_id_fkey FOREIGN KEY (test_module_id) REFERENCES test_modules(id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE assignment_test_modules DROP CONSTRAINT IF EXISTS assignment_test_modules_test_module_id_fkey;
ALTER TABLE assignment_test_modules ADD CONSTRAINT assignment_test_modules_test_module_id_fkey FOREIGN KEY (test_module_id) REFERENCES test_modules(id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE test_module_weights DROP CONSTRAINT IF EXISTS test_module_weights_test_module_id_fkey;
ALTER TABLE test_module_weights ADD CONSTRAINT test_module_weights_test_module_id_fkey FOREIGN KEY (test_module_id) REFERENCES test_modules(id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE test_results DROP CONSTRAINT IF EXISTS test_results_test_module_id_fkey;
ALTER TABLE test_results ADD CONSTRAINT test_results_test_module_id_fkey FOREIGN KEY (test_module_id) REFERENCES test_modules(id) ON DELETE CASCADE ON UPDATE CASCADE;

-- Now update the test modules with fixed UUIDs
UPDATE test_modules SET id = '00000000-0000-0000-0000-000000000001' WHERE name = 'correctness_evaluator';
UPDATE test_modules SET id = '00000000-0000-0000-0000-000000000002' WHERE name = 'performance_analyzer';
UPDATE test_modules SET id = '00000000-0000-0000-0000-000000000003' WHERE name = 'code_quality';
UPDATE test_modules SET id = '00000000-0000-0000-0000-000000000004' WHERE name = 'ai_plagiarism';
UPDATE test_modules SET id = '00000000-0000-0000-0000-000000000005' WHERE name = 'error_evaluator';
