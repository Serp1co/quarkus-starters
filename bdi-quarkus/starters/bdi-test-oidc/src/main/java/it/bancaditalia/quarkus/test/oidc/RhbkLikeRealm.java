package it.bancaditalia.quarkus.test.oidc;

import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.smallrye.jwt.build.Jwt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plays the Red Hat build of Keycloak for a test: the WireMock OIDC server of quarkus-test-oidc-server
 * (discovery document, JWKS, introspection, userinfo) on a random port, and RHBK-shaped access tokens signed
 * with its key. Returns the platform keys of bdi-config-security-oidc: realm URL, client id, client secret.
 * <p>
 * {@link #token(String, Set)} mints a bearer token for a user with the given realm roles
 * ({@code realm_access.roles}, what bdi-config-security-oidc reads), as the realm would.
 */
public class RhbkLikeRealm extends OidcWiremockTestResource {

    public static final String ISSUER = "https://server.example.com";
    public static final String AUDIENCE = "https://service.example.com";
    public static final String CLIENT_ID = "poc-client";
    public static final String CLIENT_SECRET = "poc-client-secret";
    private static final String SIGNING_KEY = "privateKey.jwk"; // bundled in quarkus-test-oidc-server, the mock's JWKS matches it

    private Map<String, String> keys;

    @Override
    public Map<String, String> start() {
        Map<String, String> wiremock = super.start(); // keycloak.url (the mock base URL, ending in /auth) and the signing key location
        keys = new LinkedHashMap<>();
        keys.put("quarkus.oidc.auth-server-url", wiremock.get("keycloak.url") + "/realms/quarkus");
        keys.put("quarkus.oidc.client-id", CLIENT_ID);
        keys.put("quarkus.oidc.credentials.secret", CLIENT_SECRET);
        keys.put("quarkus.oidc.tls.verification", "none"); // the mock realm speaks plain HTTP
        Map<String, String> all = new LinkedHashMap<>(wiremock);
        all.putAll(keys);
        return all;
    }

    /** The keys the platform would render for this realm (bdi-config-security-oidc's contract). */
    public Map<String, String> platformKeys() {
        return keys;
    }

    /** An access token as the realm issues them: preferred_username and realm_access.roles. */
    public static String token(String user, Set<String> realmRoles) {
        return Jwt.preferredUserName(user)
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(user)
                .claim("realm_access", Map.of("roles", List.copyOf(realmRoles)))
                .claim("email", user + "@bancaditalia.it")
                .jws().keyId("1")
                .sign(SIGNING_KEY);
    }
}
