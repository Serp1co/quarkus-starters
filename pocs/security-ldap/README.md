# POC security: Active Directory / LDAP

Design note §4.5, the legacy bridge first because it is where most EAP applications are: a security domain on
the corporate Active Directory, HTTP Basic or form login, `@RolesAllowed` on the beans, roles that are AD
groups. Here the same application runs on the Red Hat build of Quarkus with the Elytron LDAP realm shaped
for AD by `bdi-config-security-ldap`, and three things move from the application to the platform: the
directory connection, the *group to role* mapping per environment, and the audit trail. The code keeps
`@RolesAllowed` with application roles (`reader`, `operator`, `admin`) and never sees a group name.
Status: 8 tests and 4 integration tests green against an AD look-alike, in the sandbox and in CI.
[`security-oidc`](../security-oidc/) is the same application on the Red Hat build of Keycloak.

| Method | Path | Who | What it does |
|---|---|---|---|
| GET | `/api/public/status` | anyone | open by the platform's path policy |
| GET | `/api/me` | any authenticated user | login name, roles (AD groups plus the application roles mapped from them) |
| GET | `/api/requests`, `/api/requests/{id}`, `/api/requests/desk` | reader, operator, admin | the desk |
| POST | `/api/requests` | operator, admin | files a request (422 above `desk.max-pending`) |
| POST | `/api/requests/{id}/decision` | admin | approves or rejects |

## What replaces what

| On EAP | Here | Where |
|---|---|---|
| `<security-domain>` with an LDAP realm in `standalone.xml`, `jboss-web.xml` naming it | `bdi-security-ldap`: Elytron LDAP realm, HTTP Basic; the URL, service account and base DNs are contract keys rendered by the platform | `bdi-config-security-ldap` |
| `<login-module>` options: user filter, role filter, role attribute | standardized: users by `sAMAccountName` under the users base (recursive), password verified by binding as the user (`direct-verification`, AD never returns a password), roles from the groups whose `member` is the user's DN | `bdi-defaults.yaml` of the module |
| `<security-constraint>` in `web.xml` | path policies owned by the platform: `/api/*` authenticated, `/api/public/*` open | `bdi-config-security` |
| `@RolesAllowed("APP-ADMINS")`: the group name in the code | `@RolesAllowed("admin")`: an application role; `quarkus.http.auth.roles-mapping."APP-ADMINS-<env>"=admin,...` rendered per environment. The build derives the roles the code names into the contract; the conformance check reports `role:<name>` when an environment grants none of them | `DeskResource`, `ContractExporter`, `/q/platform` |
| principal and roles from `EJBContext` / `HttpServletRequest` | `SecurityIdentity`: principal, roles, attributes | `MeResource`, `DeskResource.file` |
| audit: none, or a valve | one JSON log line per authentication and authorization outcome (`it.bancaditalia.security.audit`), user, roles, method, path and remote as MDC fields for the SIEM; failures at WARN | `SecurityAuditLogger` in `bdi-config-security` |
| the AD for tests: a shared test domain, or none | `bdi-test-ldap`: an AD look-alike in memory (AD object classes and attributes, `sAMAccountName`, `memberOf`, `member`); your own `ad-directory.ldif` on the test classpath overrides the default users and groups | `AdLikeDirectory` |
| tests that need a real login | `@TestSecurity(user, roles)` from `bdi-test` for the resource logic; one test class against the directory for the realm itself | `DeskUnitTest`, `DeskResourceTest` |

## The AD specifics, standardized

- **Users by search, never by composed DN.** In AD the DN is `CN=Full Name,OU=...`, unrelated to the login,
  so the realm searches `(sAMAccountName=<login>)` under the users base, recursively (nested OUs), and binds
  as the DN it found.
- **Groups by membership search.** `(&(objectClass=group)(member={1}))` under the groups base, `{1}` being the
  user's DN; the group's `cn` becomes a role. On a real domain, nested groups need the matching-rule filter
  `(member:1.2.840.113556.1.4.1941:={1})`, a one-line override of the platform default. `memberOf` on the
  user entry would be the shortcut, but the Quarkus attribute mapping always searches, so it is not used.
- **Referrals ignored**: a domain controller answers with referrals to the rest of the forest; the platform
  points at a global catalog (port 3269) to see every domain without following them.
- **Identity cache** of one minute per user (`quarkus.security.ldap.cache.*`): one directory round trip per
  user per minute, not per request. Contract key, so the platform can tune it.
- **TLS**: `ldaps://` with the domain CA in the platform truststore; the tests use plain LDAP on localhost.
- **What the platform renders**: `dir-context.url`, `dir-context.principal`, `dir-context.password` (vault),
  `identity-mapping.search-base-dn`, `attribute-mappings.groups.filter-base-dn`, `roles-mapping.*`.

## The roles mapping, as verified

The AD look-alike has alice in APP-ADMINS, bob in APP-OPERATORS, carol in APP-READERS, dave in no group.
`%test` maps APP-ADMINS to admin,operator,reader; APP-OPERATORS to operator,reader; APP-READERS to reader.
`DeskResourceTest` checks: anonymous gets 401 with a Basic challenge, and 200 on `/api/public`; a wrong
password and an unknown user get 401; dave authenticates but gets 403 everywhere; a reader reads and cannot
file; an operator files and cannot decide; an admin decides. `/q/platform` reports, next to the config echo,
`roles: {admin: [APP-ADMINS], operator: [APP-ADMINS, APP-OPERATORS], reader: [...]}`.

The audit lines, from the test run (JSON in environments, one MDC field per column):

```
INFO  [it.bancaditalia.security.audit] authentication success user=alice roles=[APP-ADMINS,admin,operator,reader] GET /api/me remote=127.0.0.1
WARN  [it.bancaditalia.security.audit] authentication failure user=anonymous roles=[] GET /api/requests remote=127.0.0.1 detail=AuthenticationFailedException
WARN  [it.bancaditalia.security.audit] authorization failure user=carol roles=[APP-READERS,reader] POST /api/requests remote=127.0.0.1 detail=...RolesAllowedCheck
```

## What the developer wrote

`src/main/java/it/bancaditalia/quarkus/poc/security`: `domain` (the request record and an in-memory store,
the POC is about who may do what), `api` (three resources), `config` (`DeskConfig`). `application.yaml`:
`%dev`/`%test` values only, the roles mapping of the test directory included. Starters: `bdi-rest-jackson`,
`bdi-security-ldap`, `bdi-observability`; `bdi-test`, `bdi-test-ldap` in test scope. Nothing in the code
names a directory, a group, or Basic authentication.

## Config contract

Derived at build time: `desk.unit` (required), `desk.max-pending`; the five directory keys and the cache
age of `bdi-config-security-ldap`; `quarkus.http.auth.roles-mapping.*` of `bdi-config-security`; ports and
log level; and `roles: [admin, operator, reader]` from `@RolesAllowed`. The Ansible pre-deploy check
(`bdi_check`) refuses a rendering that grants none of the roles.

## Run it

```bash
./mvnw verify -pl pocs/security-ldap -DskipITs=false    # unit and integration tests against the AD look-alike
curl -u alice:alice-pw http://localhost:8080/api/me      # against a running instance
```

`./mvnw quarkus:dev` needs a directory: there is no Dev Service for LDAP. Point `%dev` at a test domain
through the same five keys (an `instance.yaml` in `QUARKUS_CONFIG_LOCATIONS` keeps them out of the
repository), or run the tests, which start their own.

## Measured numbers

| Metric | Value |
|---|---|
| Start-up to ready, prod profile | about 2 s in the sandbox |
| Test suite | 8 tests + 4 integration tests, green |
| Config lines owned by the developer (non-profile) | 0 |
| Lines of security code in the application | 0 beyond `@RolesAllowed` and `@Authenticated` |

## Gaps and next steps

- **Form login** for interactive legacy applications (`quarkus.http.auth.form`): same realm, a cookie instead
  of the Basic header; to add with the web UI POC (class E).
- **Kerberos / SPNEGO** intranet SSO is Quarkiverse-only (design note 4.5): flagged, not built.
- **Outbound identity** (calling another API as the user or as the service) is the OIDC POC's, where tokens
  exist; with LDAP alone there is nothing to propagate.
- **Management port authentication** (`quarkus.management.auth.*`): the platform firewalls it today; a policy
  on `/q/platform` is cookbook 12.
