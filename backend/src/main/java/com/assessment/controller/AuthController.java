package com.assessment.controller;

import com.assessment.dto.AuthResponse;
import com.assessment.dto.UserDto;
import com.assessment.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.authenticateWithPassword(request.username(), request.password());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sso/callback")
    public ResponseEntity<AuthResponse> handleSsoCallback(@AuthenticationPrincipal Jwt jwt) {
        AuthResponse response = authService.authenticateFromSso(jwt);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        AuthResponse response = authService.refreshToken(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        authService.getCurrentUser(token).ifPresent(user -> authService.logout(user.getId()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        return authService.getCurrentUser(token)
                .map(user -> ResponseEntity.ok(UserDto.fromEntity(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }

    public record LoginRequest(String username, String password) {
    }
}
