# bdi-quarkus: BOM, starters and config modules

The bank-owned layer of design note §2.3, as code. A developer never names a Quarkus extension: they add
**starters** that carry an opinionated, RHBQ-supported dependency set, and each starter brings the
**config module** that configures those extensions in a standardized way. Everything is pinned to one RHBQ
stream by the root POM, and exported to applications outside this repository through the **BOM**.

```
bdi-quarkus/
  bom/                         bdi-quarkus-bom: RHBQ platform BOM + every bdi-* artifact, for external applications
  parent/                      bdi-quarkus-parent: the parent POM of every application (BOM at a fixed platform version,
                               contract export, artifact lint, dependency governance, packaging); it/ builds two
                               applications outside the reactor with versions of their own, and the three refusals
  assembly/                    the quarkus-app zip descriptor the parent binds, so no application carries one
  config/bdi-config-core       the mechanism: loads META-INF/bdi-defaults.yaml of every module; brings YAML config
  config/bdi-config-rest       defaults + contract keys of the REST stack
  config/bdi-config-jpa        defaults + contract keys of JPA/JTA/Agroal, shared by every bdi-jpa-* variation
  config/bdi-config-jpa-xa     the approved XA build configuration (transactions=xa fixed at build time, ordinal 105)
  config/bdi-config-observability   defaults for logging/management, the /q/platform conformance endpoint, contract assembly
  config/bdi-config-scheduler  Quartz clustered on the application database, contract keys of the timers
  config/bdi-config-flyway     migrate at start, Hibernate validate (ordinal 110 over bdi-config-jpa)
  config/bdi-config-messaging-amqp   MDB-like consumer defaults, broker keys
  config/bdi-config-jms        JMS over AMQP on the same broker keys
  config/bdi-config-messaging-kafka  commit and failure strategies, cluster keys
  config/bdi-config-audit      Envers naming convention (_aud, rev, revtype, revinfo), deletions recorded
  config/bdi-config-security   path policies, group-to-role mapping key, audit log of security events (shared)
  config/bdi-config-security-ldap   Elytron LDAP realm shaped for Active Directory, Basic auth, directory keys
  config/bdi-config-security-oidc   quarkus-oidc shaped for the Red Hat build of Keycloak, realm keys
  config/bdi-config-rest-client     outbound REST: timeouts, one URL key per injected client
  config/bdi-config-oidc-client     the application's own RHBK client (client credentials) for calls as itself
  config/bdi-config-grpc            gRPC on the unified server, one host/port per injected client, bearer propagation
  starters/bdi-*               empty jars whose dependency set is the point
```

## Starters

| Starter | Brings | Configured by | RHBQ |
|---|---|---|---|
| `bdi-rest-jackson` | `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-hibernate-validator`, `quarkus-smallrye-openapi` | `bdi-config-rest` | supported |
| `bdi-jpa-postgresql` | `quarkus-hibernate-orm`, `quarkus-narayana-jta`, `quarkus-jdbc-postgresql` | `bdi-config-jpa` | supported |
| `bdi-jpa-oracle` | same, with `quarkus-jdbc-oracle` | `bdi-config-jpa` | supported |
| `bdi-jpa-db2` | same, with `quarkus-jdbc-db2` | `bdi-config-jpa` | supported |
| `bdi-observability` | `quarkus-smallrye-health`, `quarkus-logging-json`, `platform-contract` | `bdi-config-observability` | supported |
| `bdi-scheduler` | `quarkus-quartz` (clustered JDBC store on the application database) | `bdi-config-scheduler` | supported |
| `bdi-flyway-postgresql` | `quarkus-flyway`, `quarkus-flyway-postgresql` | `bdi-config-flyway` (ordinal 110: Hibernate validates, never generates) | supported |
| `bdi-messaging-amqp` | `quarkus-messaging-amqp` (Reactive Messaging over AMQP 1.0, AMQ Broker) | `bdi-config-messaging-amqp`: durable, redelivery on failure, reconnection; `amqp-*` contract keys | supported |
| `bdi-jms-amqp` | `quarkus-qpid-jms` 2.12.0 (amqphub) | `bdi-config-jms`: the JMS connection follows the `amqp-*` keys | **not in the RHBQ platform, unsupported** |
| `bdi-messaging-kafka` | `quarkus-messaging-kafka` | `bdi-config-messaging-kafka`: throttled commits, dead-letter topic, earliest; `kafka.*` contract keys | supported |
| `bdi-scheduler-local` | `quarkus-scheduler` (in-memory, per instance) | | supported |
| `bdi-jpa-xa` | none: `bdi-config-jpa-xa` fixes `quarkus.datasource.jdbc.transactions=xa` at build time (the EAP `xa-data-source`); the deploy role refuses an XA fragment for an artifact built without it | `bdi-config-jpa-xa` | supported |
| `bdi-jpa-panache` | `quarkus-hibernate-orm-panache` (repositories, active record, paging, projections); a capability next to a `bdi-jpa-*` variation | `bdi-config-jpa` | supported |
| `bdi-jpa-audit` | `quarkus-hibernate-envers` (`@Audited` history) | `bdi-config-audit` | supported |
| `bdi-security-ldap` | `quarkus-elytron-security-ldap` (Basic auth, AD-shaped realm) | `bdi-config-security-ldap` + `bdi-config-security` | supported |
| `bdi-security-oidc` | `quarkus-oidc` (bearer tokens from RHBK) | `bdi-config-security-oidc` + `bdi-config-security` | supported |
| `bdi-rest-client` | `quarkus-rest-client-jackson` (the default remote-EJB replacement) | `bdi-config-rest-client`: timeouts; `quarkus.rest-client.<client>.*` keys derived per `@RestClient` injection | supported |
| `bdi-rest-client-oidc` | `quarkus-rest-client-oidc-token-propagation` (`@AccessToken`), `quarkus-rest-client-oidc-filter` + `quarkus-oidc-client` (`@OidcClientFilter`) | `bdi-config-oidc-client`: `quarkus.oidc-client.*` keys | supported |
| `bdi-grpc` | `quarkus-grpc` (services and clients, the `.proto` as the interface) | `bdi-config-grpc`: unified server (build-time), `quarkus.grpc.clients.<client>.*` keys derived per `@GrpcClient`, bearer propagation interceptor | supported |
| `bdi-test` (test scope) | `quarkus-junit`, `quarkus-junit5-mockito` (`@InjectMock`), `quarkus-test-security` (`@TestSecurity`), `rest-assured` | | supported |
| `bdi-test-ldap` (test scope) | `quarkus-test-ldap` + `AdLikeDirectory`: an in-memory Active Directory look-alike that returns the LDAP platform keys | | supported |
| `bdi-test-oidc` (test scope) | `quarkus-test-oidc-server` + `RhbkLikeRealm`: a mock realm that returns the OIDC platform keys and mints RHBK-shaped tokens | | supported |

An application declares capabilities, and picks the variation its estate needs:

```xml
<dependency><groupId>it.bancaditalia.quarkus</groupId><artifactId>bdi-rest-jackson</artifactId></dependency>
<dependency><groupId>it.bancaditalia.quarkus</groupId><artifactId>bdi-jpa-oracle</artifactId></dependency>
<dependency><groupId>it.bancaditalia.quarkus</groupId><artifactId>bdi-observability</artifactId></dependency>
<dependency><groupId>it.bancaditalia.quarkus</groupId><artifactId>bdi-test</artifactId><scope>test</scope></dependency>
```

### Variations, not choices

Banca d'Italia runs several stacks side by side, like a public administration, so every capability is
offered as a family of starters that share one config module, and an application picks the member that
matches its estate. Planned members, to be created with the POC that exercises each (support status to be
confirmed against the RHBQ supported-configurations list, design note §9):

| Capability | Variations | Starters (planned) |
|---|---|---|
| Relational data | PostgreSQL, Oracle, Db2 | `bdi-jpa-postgresql`, `bdi-jpa-oracle`, `bdi-jpa-db2` (done); `bdi-flyway-postgresql` (done), `bdi-flyway-oracle`, Db2 to check; capabilities on top of any of them: `bdi-jpa-panache`, `bdi-jpa-audit` (done) |
| Timers | clustered Quartz on the database | `bdi-scheduler` (done) |
| Queues and topics | AMQ Broker over AMQP 1.0 (Reactive Messaging or JMS), IBM MQ (JMS, resource adapter: Quarkiverse, unsupported), Kafka / AMQ Streams | `bdi-messaging-amqp`, `bdi-jms-amqp`, `bdi-messaging-kafka` (done); `bdi-jms-ibmmq`, `bdi-kafka-streams` planned |
| Outbound calls | REST (user token or service identity), gRPC | `bdi-rest-client`, `bdi-rest-client-oidc`, `bdi-grpc` (done); messaging for fire-and-forget |
| Inbound identity | Active Directory / LDAP (Elytron), Red Hat build of Keycloak (OIDC) | `bdi-security-ldap`, `bdi-security-oidc` (done), sharing `bdi-config-security`: path policies, `roles-mapping.*`, audit log |
| Secrets | CyberArk, HashiCorp Vault, rendered file (AAP) | `bdi-secrets-cyberark`, `bdi-secrets-vault`, the file path of `bdi-config-core` |
| SOAP | Quarkus CXF | `bdi-soap-cxf` |
| Spring bridge | `quarkus-spring-*` compatibility | `bdi-spring-compat` |

## How a config module works

1. It ships `META-INF/bdi-defaults.yaml`. `bdi-config-core`'s `BdiDefaultsConfigSourceFactory` (registered
   through `META-INF/services`) loads every such file it finds on the classpath as a config source named
   `BdiDefaults[<module>]` with **ordinal 100**. Quarkus honours it at build time and at run time, so
   build-time keys (`quarkus.management.enabled`, `quarkus.banner.enabled`) work too.
2. Ordinal 100 sits above Quarkus' own defaults and below everything a developer or the platform writes:

   | Source | Ordinal |
   |---|---|
   | system properties | 400 |
   | files named in `QUARKUS_CONFIG_LOCATIONS` (rendered by the platform; first listed wins) | 300, wins the tie |
   | environment variables (`QUARKUS_DATASOURCE_JDBC_URL`) | 300 |
   | `application.yaml` in the artifact (developer, `%dev`/`%test` only) | 250 |
   | **`BdiDefaults[bdi-config-*]`** | **100** (a module may declare `x-bdi-ordinal`, e.g. 110 for `bdi-config-flyway`) |
   | Quarkus defaults | lowest |

   Profile sections (`"%dev":`, `"%test":`) in a defaults file work like anywhere else, which is how the
   inner loop gets plain logs and `drop-and-create` without a line in the application.
3. It declares its platform-owned keys in `META-INF/bdi-contract-platform.json` (a descriptor, no code:
   name, type, required, default, secret, doc; messaging modules add per-channel templates such as
   `address` or `topic`, `group.id` with `{application}` and `{channel}` placeholders). At build time the
   `bdi-application` profile of the root pom runs `ContractExporter` on every module that ships an
   `application.yaml`: it reads the application's `@ConfigMapping` interfaces and `@Incoming`/`@Outgoing`/
   `@Channel` names from the compiled classes, merges the descriptors found on the classpath and writes
   `META-INF/config-contract.json` into the artifact. Nobody writes a contract: the application declares
   what it reads, the platform derives what it must render, and `/q/platform` echoes the result.
4. It is a plain jar with a Jandex index (for its CDI beans), not a Quarkus extension. The day a module
   needs build steps (enforcing, generating, dev-UI cards), it becomes one without changing its users.

Rules: a default is always overridable; a default is never an environment value; every platform key a
module relies on is in its `bdi-contract-platform.json`; the module's README line in the table above says what
it standardizes.

## Parent POM: what an application inherits

Applications inherit `bdi-quarkus-parent` (`bdi-quarkus/parent`), not this repository's parent: the
repository parent manages its own artifacts at `${project.version}`, which in an application resolves to the
*application's* version. `bdi-quarkus-parent` is standalone and imports `bdi-quarkus-bom` at the explicit
`bdi.platform.version`, so an application at 7.2.0 resolves `bdi-rest-jackson` at the platform's version
(`parent/it/run.sh` proves it, and that the parent's own version and the platform version it imports are the
same release). It binds what a governed build must run and an application must not skip:

| Bound in the parent | What it does |
|---|---|
| `ContractExporter` (process-classes) | derives `META-INF/config-contract.json` from the compiled classes and the module descriptors |
| `ArtifactConfigLint` (process-classes) | fails the build on environment profiles, packaged secrets, platform runtime keys in the packaged configuration |
| enforcer `bannedDependencies` | no direct `io.quarkus`, `io.quarkiverse`, SmallRye, Hibernate, JBoss, Vert.x or MicroProfile dependency (starters only); no platform artifact at another version |
| `quarkus-maven-plugin`, surefire, failsafe, assembly | the fast-jar, the tests, `-DskipITs=false` integration tests, the `quarkus-app` zip the deploy role installs |

Applications that cannot inherit it (a corporate parent of their own) import the BOM and copy the plugin
section of `parent/it/bom-only-app/pom.xml`; the deploy role refuses an artifact without a contract either
way. Overrides of third-party versions the BOM manages are not caught by the enforcer: the release pipeline
diffs the resolved dependency graph against the BOM (pending, cookbook 2).

## BOM

Applications that do not inherit the repository parent import `bdi-quarkus-bom` once and get the RHBQ
platform BOM plus every `bdi-*` artifact at the same version; they still declare the
`com.redhat.quarkus.platform:quarkus-maven-plugin` at the platform version.
