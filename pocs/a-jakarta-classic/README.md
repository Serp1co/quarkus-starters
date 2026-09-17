# POC A: Jakarta classic on the Red Hat build of Quarkus

Migration class A of the design notes (§4.1): an application whose EAP footprint is **JAX-RS, CDI, JPA,
JTA and Bean Validation**, moved to RHBQ 3.33 (Quarkus REST, ArC, Hibernate ORM 7, Narayana, Hibernate
Validator). Status: first cut, all tests green in JVM mode against PostgreSQL, replayed on stage 1 of the
target ladder (fast-jar + rendered configuration); no AAP role yet.

The application is a small **ledger**: current accounts identified by IBAN and transfers between them,
executed in one JTA transaction under rules (maximum amount, blocked IBANs, mandatory reference) that the
platform sets per environment. Small on purpose: what matters is that every EAP construct of class A is
present once, and that the developer's configuration ends up (almost) empty.

| Method | Path | What it does |
|---|---|---|
| GET | `/api/accounts` | list accounts |
| POST | `/api/accounts` | open an account (Bean Validation on the body, custom `@Iban` constraint) |
| GET | `/api/accounts/{iban}` | one account, 404 through a JAX-RS `ExceptionMapper` |
| GET | `/api/accounts/{iban}/transfers` | transfers of an account (JPQL with `join fetch`) |
| POST | `/api/transfers` | move money: two updates and one insert in one transaction; 422 on a business rule, rolled back |
| GET | `/api/transfers/{id}` | one transfer |
| GET | `:9000/q/health/ready` | readiness including the database check (the load balancer gate) |
| GET | `:9000/q/platform` | conformance check: version, profile, config source of every contract key |
| GET | `:9000/q/openapi` | OpenAPI document |

## What the developer wrote

Everything under [`src/main/java/it/bancaditalia/quarkus/poc/jakarta`](src/main/java/it/bancaditalia/quarkus/poc/jakarta):

| Package | Content | Compared with EAP |
|---|---|---|
| `domain` | `Account`, `Transfer` JPA entities with named queries and `@Version`; unchecked domain exceptions | unchanged |
| `repository` | `AccountRepository`, `TransferRepository`: `EntityManager` and JPQL | `@Stateless` + `@PersistenceContext` became `@ApplicationScoped` + `@Inject` (`@PersistenceContext` also works) |
| `service` | `AccountService`, `TransferService`: the business transactions | `@Stateless` became `@ApplicationScoped` + `@Transactional`; `@ApplicationException(rollback=true)` became a RuntimeException |
| `api` | JAX-RS resources, record DTOs with constraints, `@Provider` exception mappers | unchanged (Jakarta REST 3.1 has no 422 constant, see cookbook 5) |
| `validation` | `@Iban` constraint with a mod-97 `ConstraintValidator` | unchanged |
| `config` | `LedgerConfig`, one `@ConfigMapping` interface: the developer's half of the contract | replaces JNDI env entries and system-property lookups |
| `platform` | `PlatformContract` (the contract, defined once) and `ConformanceEndpoint` (`/q/platform`) | new; destined for the internal `bdi-quarkus-observability` extension |

Configuration the developer owns, [`src/main/resources/application.properties`](src/main/resources/application.properties):
**one** build-time line (`quarkus.management.enabled=true`, itself a candidate for the internal extension)
plus `%dev`/`%test` inner-loop keys. No `%prod`, no URL, no password, no port: `ArtifactConfigLintTest`
fails the build if one appears. Deleted from the EAP version: `beans.xml`, `persistence.xml`,
`web.xml`, `jboss-web.xml`.

Tests, [`src/test/java`](src/test/java): `@QuarkusTest` + RestAssured for the API (`AccountResourceTest`,
`TransferResourceTest`), `@Inject`-ed services for the transaction semantics (`TransferServiceTest` proves
that a failed debit rolls back the credit and the inserted row), the management endpoint
(`ConformanceEndpointTest`), the contract file sync (`ConfigContractTest`), the lint
(`ArtifactConfigLintTest`), and the same API tests against the packaged fast-jar
(`*IT`, `@QuarkusIntegrationTest`). Continuous testing runs all of them on every save in dev mode.

### Extensions and their support status

| Extension | Role | RHBQ 3.33 |
|---|---|---|
| `quarkus-rest`, `quarkus-rest-jackson` | JAX-RS + JSON | supported |
| `quarkus-arc` | CDI | supported |
| `quarkus-hibernate-orm` | JPA | supported |
| `quarkus-narayana-jta` | JTA | supported |
| `quarkus-hibernate-validator` | Bean Validation | supported |
| `quarkus-jdbc-postgresql` | driver (PostgreSQL stands in for the bank's Oracle/DB2 until POC C) | supported |
| `quarkus-smallrye-health` | readiness/liveness | supported, platform baseline |
| `quarkus-logging-json` | JSON logs to stdout | supported, platform baseline |
| `quarkus-smallrye-openapi` | OpenAPI | supported, platform baseline |
| `quarkus-junit`, `rest-assured` | tests | supported (`quarkus-junit5` is a relocation since 3.31) |

Nothing from Quarkiverse. Re-check the list against the RHBQ supported-configurations article when the
stream changes (design note §9).

## What the platform provided

See [platform/README.md](platform/README.md) for the replayable stage-1 walkthrough. In short:

- the rendered [`platform/config/application.properties`](platform/config/application.properties) and
  [`application-uat.properties`](platform/config/application-uat.properties), at a fixed path;
- the vault-delivered [`platform/secrets/secrets.properties`](platform/secrets/secrets.properties), same
  path on every stage;
- two environment variables in the unit, `QUARKUS_CONFIG_LOCATIONS` and `QUARKUS_PROFILE`;
- the runtime: JDK, service user, ports, JVM flags, journald, the rolling update gated on
  `/q/health/ready`, the conformance check on `/q/platform`;
- for stages 2 and 3, the UBI image built from [`src/main/docker/Dockerfile.jvm`](src/main/docker/Dockerfile.jvm)
  and the same two variables in the Quadlet unit or the Deployment.

## Config contract

Exported to [`src/main/resources/META-INF/config-contract.json`](src/main/resources/META-INF/config-contract.json)
(inside the artifact, kept in sync by `ConfigContractTest`; refresh with
`./mvnw test -Dconfig-contract.update=true`). AAP validates the rendered file against it before a deploy.

| `ledger.currency` | String | no | `EUR` | application | ISO 4217 code of the currency every account of this ledger is denominated in |
| `ledger.transfer.blocked-ibans` | list<String> | no |  | application | IBANs that may neither send nor receive, e.g. the sanctions list feed; comma separated |
| `ledger.transfer.max-amount` | BigDecimal | yes |  | application | Upper bound for a single transfer; the platform sets it per environment |
| `ledger.transfer.require-reference` | boolean | no | `false` | application | Reject transfers that carry no reference (causale) |
| `quarkus.datasource.jdbc.url` | String | yes |  | platform | JDBC URL of the ledger database (Agroal pool) |
| `quarkus.datasource.username` | String | yes |  | platform | Database user of this application |
| `quarkus.datasource.password` | String | yes |  | platform (secret) | Database password, delivered from the vault |
| `quarkus.datasource.jdbc.max-size` | int | no | `50` | platform | Upper bound of the connection pool |
| `quarkus.http.port` | int | no | `8080` | platform | Application port, the load balancer target |
| `quarkus.management.port` | int | no | `9000` | platform | Management port: health, conformance endpoint |
| `quarkus.log.level` | String | no | `INFO` | platform | Root log level |

## Run it

```bash
# inner loop (from the repository root)
./mvnw quarkus:dev -pl pocs/a-jakarta-classic      # Dev Services start PostgreSQL; import-dev.sql seeds three accounts
#   Dev UI at localhost:8080/q/dev-ui, Swagger UI at localhost:9000/q/swagger-ui (management port), 'r' runs the tests
./mvnw verify                                       # tests; -DskipITs=false adds the integration tests on the fast-jar
# build once
./mvnw package                                      # target/quarkus-app/ (fast-jar)
podman build -f pocs/a-jakarta-classic/src/main/docker/Dockerfile.jvm -t poc-a-jakarta-classic:jvm pocs/a-jakarta-classic
# run like the platform (stage 1 replay, needs a PostgreSQL at localhost:5432, database/user/password poc)
pocs/a-jakarta-classic/platform/run-stage1.sh
```

Without a container runtime, point the inner loop at any PostgreSQL through the platform's own keys and
Dev Services stay out of the way: `export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://host:5432/db
QUARKUS_DATASOURCE_USERNAME=... QUARKUS_DATASOURCE_PASSWORD=...`. To pull the Dev Services image from the
bank's registry instead of Docker Hub, set `%dev.quarkus.datasource.devservices.image-name`.

## Measured numbers

Sandbox: 4 vCPU, 16 GB, OpenJDK 21.0.10, Maven cache warm, PostgreSQL 16 on the same host, JVM mode.
Numbers to be re-taken on the bank's VM class with the standardized JVM flags; they are here so the
table exists from day one (design note §4.6).

| Metric | Value |
|---|---|
| Start-up to ready, prod profile, fast-jar | 2.4 s (Quarkus "started in") |
| RSS after 300 requests | 268 MB with default JVM flags, 272 MB with `-Xmx256m` (the heap is not the driver at this load) |
| Artifact size (`target/quarkus-app/`) | 51 MB |
| Clean `package` without tests, warm cache | 10 s |
| Test suite | 20 tests (`@QuarkusTest`) + 13 integration tests, all green |
| Config lines owned by the developer (non-profile) | 1 (`quarkus.management.enabled`) |
| Throughput | not measured in the sandbox (no load tool); take it on the target VM class |
| Deploy and rollback time | needs the AAP job template (cookbook 14) |

## Gaps and next steps

- **Schema management.** Hibernate creates the schema in `%dev`/`%test` only; environments need the DDL
  from the DBA (as on EAP) or Flyway (cookbook 4). Decide per team.
- **Database.** PostgreSQL here; Oracle/DB2 drivers and named datasources are POC C.
- **Security.** `/q/platform` echoes non-secret configuration; the management port must be firewalled to
  ops and the LB by the platform, and the endpoint gets a path policy in cookbook 12.
- **Observability.** JSON logs and health only; Micrometer and OpenTelemetry arrive with cookbook 13, ideally
  through the `bdi-quarkus-observability` extension so that this POC's `platform` package and its one
  build-time line disappear from application code.
- **AAP.** The job template deploying this artifact on the three stages (cookbooks 14 to 16), and the
  contract validation task that reads `config-contract.json`.
- **Concurrency.** `@Version` detects lost updates; a mapper turning `OptimisticLockException` into 409
  and a retry policy are left for the EJB-heavy POC, where the question really matters.
