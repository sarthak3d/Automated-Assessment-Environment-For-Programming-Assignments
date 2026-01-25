package com.assessment.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SsoAuthenticationConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Logger log = LoggerFactory.getLogger(SsoAuthenticationConverter.class);

    @Value("${backend.sso.rolesClaim:roles}")
    private String rolesClaim;

    @Value("${backend.sso.rolesPrefix:ROLE_}")
    private String rolesPrefix;

    private static final Map<String, String> ROLE_MAPPINGS = Map.of(
        "student", "STUDENT",
        "teacher", "TEACHER",
        "instructor", "TEACHER",
        "professor", "TEACHER",
        "admin", "ADMIN",
        "administrator", "ADMIN"
    );

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<String> roles = extractRoles(jwt);
        
        return roles.stream()
            .map(this::mapRole)
            .filter(Objects::nonNull)
            .map(role -> new SimpleGrantedAuthority(rolesPrefix + role))
            .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private Collection<String> extractRoles(Jwt jwt) {
        Set<String> roles = new HashSet<>();

        Object rolesValue = jwt.getClaim(rolesClaim);
        if (rolesValue instanceof Collection) {
            roles.addAll((Collection<String>) rolesValue);
        } else if (rolesValue instanceof String) {
            roles.add((String) rolesValue);
        }

        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map) {
            Map<String, Object> realmAccessMap = (Map<String, Object>) realmAccess;
            Object realmRoles = realmAccessMap.get("roles");
            if (realmRoles instanceof Collection) {
                roles.addAll((Collection<String>) realmRoles);
            }
        }

        Object resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess instanceof Map) {
            Map<String, Object> resourceAccessMap = (Map<String, Object>) resourceAccess;
            resourceAccessMap.values().forEach(resource -> {
                if (resource instanceof Map) {
                    Object resourceRoles = ((Map<String, Object>) resource).get("roles");
                    if (resourceRoles instanceof Collection) {
                        roles.addAll((Collection<String>) resourceRoles);
                    }
                }
            });
        }

        Object groups = jwt.getClaim("groups");
        if (groups instanceof Collection) {
            ((Collection<String>) groups).stream()
                .map(g -> g.replace("/", "").toLowerCase())
                .forEach(roles::add);
        }

        log.debug("Extracted roles from JWT: {}", roles);
        return roles;
    }

    private String mapRole(String role) {
        String normalizedRole = role.toLowerCase().trim();
        
        if (ROLE_MAPPINGS.containsKey(normalizedRole)) {
            return ROLE_MAPPINGS.get(normalizedRole);
        }

        for (Map.Entry<String, String> entry : ROLE_MAPPINGS.entrySet()) {
            if (normalizedRole.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        log.debug("Unknown role: {}", role);
        return null;
    }
}
