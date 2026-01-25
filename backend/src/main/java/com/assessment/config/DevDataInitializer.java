package com.assessment.config;

import com.assessment.model.User;
import com.assessment.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("development")
public class DevDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        createTestUser("admin", "admin@assessment.local", "Admin", "User", User.Role.ADMIN, "admin123");
        createTestUser("teacher", "teacher@assessment.local", "Milton", "Instructor", User.Role.TEACHER, "teacher123");
        createTestUser("student", "student@assessment.local", "Ishan", "Learner", User.Role.STUDENT, "student123");

        log.info("=== Development Test Accounts ===");
        log.info("  admin   / admin123   (ADMIN)");
        log.info("  teacher / teacher123 (TEACHER)");
        log.info("  student / student123 (STUDENT)");
        log.info("  Login: POST /api/v1/auth/login");
        log.info("=================================");
    }

    private void createTestUser(String username, String email, String firstName, String lastName,
                                User.Role role, String rawPassword) {
        userRepository.findByUsername(username).ifPresentOrElse(
            existing -> {
                existing.setPasswordHash(passwordEncoder.encode(rawPassword));
                userRepository.save(existing);
                log.info("Updated password for test user: {} ({})", username, role);
            },
            () -> {
                User user = User.builder()
                    .username(username)
                    .email(email)
                    .ssoId("dev-" + username + "-sso")
                    .firstName(firstName)
                    .lastName(lastName)
                    .role(role)
                    .active(true)
                    .department("Development")
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .build();
                userRepository.save(user);
                log.info("Created test user: {} ({})", username, role);
            }
        );
    }
}
