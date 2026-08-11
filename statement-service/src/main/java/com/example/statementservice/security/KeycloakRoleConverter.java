package com.example.statementservice.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String JWT_CLAIM_ROLES = "roles";
    public static final String REALM_ACCESS = "realm_access";
    public static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        var roles = jwt.getClaimAsStringList(JWT_CLAIM_ROLES);

        if (roles == null || roles.isEmpty()) {
            var realmAccessObj = jwt.getClaim(REALM_ACCESS);
            if (realmAccessObj instanceof Map<?, ?> realmAccess) {
                var rawRoles = realmAccess.get(JWT_CLAIM_ROLES);
                if (rawRoles instanceof List<?> list) {
                    roles = list.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .collect(Collectors.toList());
                }
            }
        }

        if (roles == null) {
            roles = List.of();
        }

        return roles.stream()
                .map(role -> ROLE_PREFIX + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
