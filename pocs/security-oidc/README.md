# POC security: OIDC / Red Hat build of Keycloak

Design note §4.5, the target: the same authorization desk as [`security-ldap`](../security-ldap/), byte for
byte the same application code, on `bdi-security-oidc` instead of `bdi-security-ldap`. Bearer tokens issued by
the Red Hat build of Keycloak (RHBK), verified against the realm's keys; roles from the realm roles claim,
mapped to the application roles by the platform exactly as AD groups were. Status: 8 tests and 4 integration
tests green against an RHBK look-alike (the WireMock OIDC server of `quarkus-test-oidc-server`), in the sandbox
and in CI.

## What changed from the LDAP variation

| | `security-ldap` | `security-oidc` |
|---|---|---|
| starter | `bdi-security-ldap` | `bdi-security-oidc` |
| credential | user and password, Basic header, verified by the directory on each cache miss | a JWT, Bearer header, verified locally against the realm's JWKS (no round trip per request) |
| challenge | `WWW-Authenticate: basic` | `WWW-Authenticate: bearer` |
| identity source | `sAMAccountName`, groups by `member` search | `preferred_username`, `realm_access.roles` (`quarkus.oidc.roles.role-claim-path`) |
| platform keys | URL, service account and password, two base DNs | `auth-server-url`, `client-id`, `credentials.secret` (vault), `token.audience`, `application-type` |
| roles mapping | `"APP-ADMINS": admin,...` | `"app-admins": admin,...` (realm roles) |
| test double | `bdi-test-ldap` (`AdLikeDirectory`) | `bdi-test-oidc` (`RhbkLikeRealm`, `RhbkLikeRealm.token(user, realmRoles)`) |
| dev mode | needs a directory | `quarkus-keycloak-devservices`: a Keycloak container with a dev realm, until `auth-server-url` is set |
| application code | identical | identical |
| audit lines | identical | identical |

`DeskResourceTest` is the LDAP one with tokens instead of passwords, plus a forged signature and a garbled
token, both 401. `DeskUnitTest` (`@TestSecurity`) and `ConformanceEndpointTest` are the same shape.

## What the standard sets (`bdi-config-security-oidc`)

`application-type: service` (an API verifies bearer tokens; a web application overrides to `web-app` for the
code flow with cookies), realm roles as the role claim, discovery with three retries two seconds apart so a
cold start survives a realm that is still coming up, TLS verification required (the RHBK chain is in the
platform truststore; the tests turn it off for the plain-HTTP look-alike, a value the platform never renders),
no userinfo round trip.

## Gaps and next steps (cookbook 12)

- **Outbound**: REST client with token propagation (the user's token forwarded) and the client-credentials
  filter (the service's own identity), `quarkus-rest-client-oidc-token-propagation` / `-oidc-filter`.
- **web-app flow** for browser applications: the code flow, session cookie, logout; with the web UI POC.
- **Multi-tenancy**: one realm per business unit resolved by path or header (`quarkus.oidc.<tenant>.*`).
- **Token exchange** for identity propagation across services, the remote-EJB replacement's concern.
- **Opaque tokens**: introspection instead of local verification when the realm issues them.
