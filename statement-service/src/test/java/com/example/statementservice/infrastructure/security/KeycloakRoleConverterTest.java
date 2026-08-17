package com.example.statementservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("token").header("alg", "none").subject("user");
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void GivenFlatRolesClaim_WhenConverting_ThenEachRoleBecomesPrefixedAuthority() {
        // Given
        var jwt = jwtWithClaims(Map.of("roles", List.of("Upload", "Search")));

        // When
        var authorities = converter.convert(jwt);

        // Then
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_Upload", "ROLE_Search");
    }

    @Test
    void GivenFlatRolesAndRealmAccessBothPresent_WhenConverting_ThenFlatRolesWin() {
        // Given
        var jwt = jwtWithClaims(Map.of(
                "roles", List.of("Upload"),
                "realm_access", Map.of("roles", List.of("Search"))));

        // When
        var authorities = converter.convert(jwt);

        // Then
        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_Upload");
    }

    @Test
    void GivenOnlyNestedRealmAccessRoles_WhenConverting_ThenFallbackPathMapsThem() {
        // Given: the shape a real Keycloak access token carries
        var jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("GenerateSignedLink"))));

        // When
        var authorities = converter.convert(jwt);

        // Then
        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_GenerateSignedLink");
    }

    @Test
    void GivenEmptyFlatRolesClaim_WhenConverting_ThenRealmAccessFallbackIsUsed() {
        // Given
        var jwt = jwtWithClaims(Map.of(
                "roles", List.of(),
                "realm_access", Map.of("roles", List.of("Search"))));

        // When
        var authorities = converter.convert(jwt);

        // Then
        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_Search");
    }

    @Test
    void GivenRealmAccessRolesWithNonStringEntries_WhenConverting_ThenOnlyStringsAreMapped() {
        // Given
        var jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("Upload", 42, Map.of("k", "v")))));

        // When
        var authorities = converter.convert(jwt);

        // Then
        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_Upload");
    }

    @Test
    void GivenNeitherRolesNorRealmAccessClaim_WhenConverting_ThenNoAuthoritiesAreGranted() {
        // Given
        var jwt = jwtWithClaims(Map.of("other", "claim"));

        // When
        var authorities = converter.convert(jwt);

        // Then
        assertThat(authorities).isEmpty();
    }

    @Test
    void GivenRealmAccessThatIsNotAMap_WhenConverting_ThenNoAuthoritiesAreGranted() {
        // Given
        var jwt = jwtWithClaims(Map.of("realm_access", "not-a-map"));

        // When
        var authorities = converter.convert(jwt);

        // Then
        assertThat(authorities).isEmpty();
    }
}
