package com.assessment.service;

import com.assessment.dto.AuthResponse;
import com.assessment.dto.UserDto;
import com.assessment.model.User;
import com.assessment.repository.UserRepository;
import com.assessment.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final GitLabService gitLabService;
    private final PasswordEncoder passwordEncoder;

    private static final String SESSION_PREFIX = "session:";
    private static final long SESSION_DURATION_HOURS = 8;

    public AuthService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider,
                       RedisTemplate<String, Object> redisTemplate, GitLabService gitLabService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.gitLabService = gitLabService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse authenticateWithPassword(String username, String password) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        if (!user.isActive()) {
            throw new IllegalStateException("User account is disabled");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User '{}' authenticated via password", username);
        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse authenticateFromSso(Jwt ssoJwt) {
        String ssoId = ssoJwt.getSubject();
        String username = extractUsername(ssoJwt);
        String email = ssoJwt.getClaim("email");
        String firstName = ssoJwt.getClaim("given_name");
        String lastName = ssoJwt.getClaim("family_name");
        User.Role role = extractRole(ssoJwt);

        User user = userRepository.findBySsoId(ssoId)
            .orElseGet(() -> createUserFromSso(ssoId, username, email, firstName, lastName, role));

        updateUserIfNeeded(user, email, firstName, lastName, role);

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return generateAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String currentToken) {
        if (!jwtTokenProvider.validateToken(currentToken)) {
            throw new IllegalArgumentException("Invalid token");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(currentToken);
        String sessionKey = SESSION_PREFIX + userId;

        String storedToken = (String) redisTemplate.opsForValue().get(sessionKey);
        if (storedToken == null || !storedToken.equals(currentToken)) {
            throw new IllegalStateException("Session not found or token mismatch");
        }

        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new IllegalStateException("User not found"));

        return generateAuthResponse(user);
    }

    public void logout(UUID userId) {
        String sessionKey = SESSION_PREFIX + userId.toString();
        redisTemplate.delete(sessionKey);
        log.info("User {} logged out", userId);
    }

    public Optional<User> getCurrentUser(String token) {
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return Optional.empty();
        }

        String userId = jwtTokenProvider.getUserIdFromToken(token);
        return userRepository.findById(UUID.fromString(userId));
    }

    private User createUserFromSso(String ssoId, String username, String email,
                                   String firstName, String lastName, User.Role role) {
        log.info("Creating new user from SSO: username={}, role={}", username, role);

        User user = User.builder()
            .ssoId(ssoId)
            .username(username)
            .email(email)
            .firstName(firstName != null ? firstName : "")
            .lastName(lastName != null ? lastName : "")
            .role(role)
            .active(true)
            .build();

        user = userRepository.save(user);

        try {
            Long gitlabUserId = gitLabService.createOrGetUser(user);
            user.setGitlabUserId(gitlabUserId);
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Failed to create GitLab user for {}: {}", username, e.getMessage());
        }

        return user;
    }

    private void updateUserIfNeeded(User user, String email, String firstName,
                                    String lastName, User.Role role) {
        boolean updated = false;

        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            updated = true;
        }
        if (firstName != null && !firstName.equals(user.getFirstName())) {
            user.setFirstName(firstName);
            updated = true;
        }
        if (lastName != null && !lastName.equals(user.getLastName())) {
            user.setLastName(lastName);
            updated = true;
        }
        if (role != user.getRole()) {
            user.setRole(role);
            updated = true;
        }

        if (updated) {
            userRepository.save(user);
        }
    }

    private AuthResponse generateAuthResponse(User user) {
        String token = jwtTokenProvider.generateToken(
            user.getId(),
            user.getUsername(),
            user.getRole().name(),
            Map.of(
                "email", user.getEmail(),
                "fullName", user.getFullName()
            )
        );

        String sessionKey = SESSION_PREFIX + user.getId().toString();
        redisTemplate.opsForValue().set(sessionKey, token, SESSION_DURATION_HOURS, TimeUnit.HOURS);

        return new AuthResponse(
            token,
            "Bearer",
            SESSION_DURATION_HOURS * 3600,
            UserDto.fromEntity(user)
        );
    }

    private String extractUsername(Jwt jwt) {
        String username = jwt.getClaim("preferred_username");
        if (username == null) {
            username = jwt.getClaim("username");
        }
        if (username == null) {
            username = jwt.getSubject();
        }
        return username;
    }

    @SuppressWarnings("unchecked")
    private User.Role extractRole(Jwt jwt) {
        Object roles = jwt.getClaim("roles");
        if (roles instanceof java.util.Collection) {
            java.util.Collection<String> roleList = (java.util.Collection<String>) roles;
            if (roleList.stream().anyMatch(r -> r.toLowerCase().contains("admin"))) {
                return User.Role.ADMIN;
            }
            if (roleList.stream().anyMatch(r -> r.toLowerCase().contains("teacher") ||
                                                r.toLowerCase().contains("instructor"))) {
                return User.Role.TEACHER;
            }
        }
        return User.Role.STUDENT;
    }
}
