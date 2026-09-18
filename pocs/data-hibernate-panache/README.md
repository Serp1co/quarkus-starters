# POC data: Hibernate ORM and Panache

The data layer of an EAP application, on the Red Hat build of Quarkus. Two questions from the teams: *do I
have to rewrite my DAOs?* (no) and *what does Panache give me if I do?* (less code, same Hibernate). The POC
answers both with one entity model, the register of supervised intermediaries, queried twice: by the DAO
carried over from EAP (`EntityManager`, named queries, Criteria API, native SQL) and by a Panache repository.
One test runs both and expects the same answers. Around them, what Hibernate ORM brings on the estate:
sequences and batching, optimistic locking, the second-level cache for reference data, Envers history, a
Flyway-owned schema. Status: 12 tests and 5 integration tests green in the sandbox and in CI.

| Method | Path | What it does |
|---|---|---|
| POST | `/api/intermediaries` | registers an intermediary (ABI, name, type, headquarters); 422 on a business rule, 400 on validation |
| GET | `/api/intermediaries?type=&status=&q=&province=&page=&size=&sort=&dir=` | paged search, projection of four columns, `X-Total-Count` header; the page size is capped by `registry.max-page-size` |
| GET | `/api/intermediaries/{abi}` | the full record, with its `version` |
| PUT | `/api/intermediaries/{abi}/status` | compare-and-set: `{status, version}`; a stale version is 409 |
| GET | `/api/intermediaries/{abi}/history` | every revision from the Envers audit tables (ADD, MOD, DEL, with the state at each) |
| POST/GET | `/api/intermediaries/{abi}/branches` | branches: the active-record variation |
| GET | `/api/intermediaries/{abi}/branches/by-province` | a native SQL report |
| GET | `/api/registry`, `/api/registry/types` | overview and cached reference data |
| POST | `/api/registry/types/{code}/suspend` | a bulk update in one statement |

## What replaces what

| On EAP | Here | Where |
|---|---|---|
| `@Stateless` DAO with `@PersistenceContext EntityManager` | `@ApplicationScoped` + `@Inject EntityManager`; the JPQL, named queries, Criteria API and native queries are untouched | `legacy/IntermediaryDao` |
| the same DAO, rewritten | `PanacheRepositoryBase<Intermediary, Long>`: `find("abi", abi)`, `count("status", s)`, `update(...)`, simplified query strings with named parameters in a map, `Sort`, `Page`, `project(Class)` | `panache/IntermediaryRepository` |
| the entity | unchanged: private fields, accessors, `@Version`, `@Embedded`, `@NamedQuery`. Panache repositories work on any JPA entity | `domain/Intermediary` |
| an entity that is its own DAO (never on EAP, but teams ask) | `PanacheEntityBase` with public fields and static finders. Fine for small self-contained entities; not mockable without `PanacheMock` | `domain/Branch` |
| `@GeneratedValue(IDENTITY)` | `SEQUENCE` with `allocationSize = 50`: ids assigned in memory, inserts batched (`statement-batch-size: 50` from `bdi-config-jpa`); IDENTITY switches batching off. Oracle and Db2 estates use sequences anyway | `Intermediary`, `V1__registry.sql` |
| `@Version` and `OptimisticLockException` at commit | the client sends the version it read; the service checks it and flushes inside the method, so the 409 is deterministic; Hibernate's own `OptimisticLockException` (a race between check and flush) maps to the same 409 | `RegistryService.changeStatus`, `RegistryExceptionMappers` |
| Infinispan second-level cache in `persistence.xml` | `@Cacheable` on reference data; Caffeine, local to the instance, TTL in `application.yaml`. No cluster-wide invalidation, see below | `IntermediaryType`, `ReferenceDataCacheTest` |
| Envers, or a trigger and a shadow table | `@Audited`, `AuditReader` for the history; naming standardized by `bdi-config-audit` (`_aud`, `rev`, `revtype`, `revinfo`); the audit tables are in the Flyway migration like every other table | `audit/HistoryService`, `V1__registry.sql` |
| `hibernate.cfg.xml` / `persistence.xml` tuning | `bdi-config-jpa`: batch size, fetch size, `fetch.batch-size: 16` against N+1, SQL logging in dev only | `bdi-quarkus/config/bdi-config-jpa` |
| `@TransactionAttribute` on the DAO | none on the DAO: transactions belong to the service (`@Transactional`); reads outside a transaction use a request-scoped session | `service/RegistryService` |
| mocking the DAO with a fake `EntityManager` | `@InjectMock IntermediaryRepository` (Mockito, from `bdi-test`): a service test without a database | `RegistryServiceMockTest` |

## Which one, when

- **Keep the DAO** when it works and is tested. The migration cost of a data layer written against
  `EntityManager` is zero, as `IntermediaryDao` shows; do not spend the budget there.
- **Write new code as a Panache repository** (`IntermediaryRepository`): the same Hibernate, a third of the
  lines for the routine queries, the `EntityManager` one call away for the rest (native reports, stored
  procedures, `LockModeType`). It is a CDI bean: injectable, mockable, and the entity stays a plain JPA class.
- **Active record** (`Branch extends PanacheEntityBase`) for small entities with no business rules and no need
  to be mocked. Public fields are rewritten to accessors at build time; the entity carries the persistence API,
  which teams either love or refuse. The POC does not recommend it as the default.
- **Never mix both on one entity**: pick one shape per entity, or the tests stop being obvious.

## The second-level cache, honestly

`IntermediaryType` is loaded once per instance and then served from memory: `ReferenceDataCacheTest` proves
the second lookup runs no SQL. The cache is Caffeine inside the JVM. With two instances (POC B's deployment),
a change to reference data on one is invisible to the other until its entry expires: hence the TTL
(`max-idle: 10M`) in `application.yaml`, the one non-profile line of the POC, and the rule *reference data
only, never a row someone edits*. Infinispan as a shared cache is possible (`quarkus-hibernate-orm` supports
it through the Infinispan client) and out of scope until a POC needs it.

## What the developer wrote

`src/main/java/it/bancaditalia/quarkus/poc/data`: `domain` (three entities, one embeddable), `query` (the
operations interface and its records), `legacy` (the DAO), `panache` (two repositories), `service`, `audit`,
`api`, `config` (`RegistryConfig`, the `@ConfigMapping`). `application.yaml` holds `%dev`/`%test` values and
the cache TTL. Starters: `bdi-rest-jackson`, `bdi-jpa-postgresql`, `bdi-jpa-panache`, `bdi-jpa-audit`,
`bdi-flyway-postgresql`, `bdi-observability`, `bdi-test`. `V1__registry.sql` is the schema, audit tables and
sequences included, validated by Hibernate at start.

Tests: `IntermediaryQueriesTest` (the two implementations against the same seed, in a rolled-back
`@TestTransaction`), `IntermediaryResourceTest` (rules, paging cap, optimistic locking, history, branches,
native report), `ReferenceDataCacheTest` (Hibernate statistics: one cache hit, no statement),
`RegistryServiceMockTest` (`@InjectMock` on the repository), `ConformanceEndpointTest`,
`ArtifactConfigLintTest`, and `IntermediaryResourceIT` on the packaged artifact under the prod profile.

## Config contract

Derived at build time: `registry.supervisor` (required, per environment), `registry.abi-pattern`,
`registry.max-page-size`, then the platform keys of the config modules in use (datasource, XA, Flyway,
ports, log level). `bdi-config-audit` adds none: every Envers key is fixed in the artifact.

## Run it

```bash
./mvnw quarkus:dev -pl pocs/data-hibernate-panache                 # Dev Services PostgreSQL, SQL logged and formatted
./mvnw verify -pl pocs/data-hibernate-panache -DskipITs=false      # unit and integration tests
```

In the sandbox (no container runtime) point the tests at a local PostgreSQL with `QUARKUS_DATASOURCE_JDBC_URL`,
`QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`, as for the other POCs.

## Measured numbers

| Metric | Value |
|---|---|
| Start-up to ready, prod profile (Flyway validate, Envers, cache) | about 4 s in the sandbox |
| Test suite | 12 tests + 5 integration tests, green |
| Config lines owned by the developer (non-profile) | 1 (the reference-data cache TTL) |
| Lines of the search operation (filters, sort, page, count) | Criteria API 43, Panache 28 |

## Things that bit

- **`@TestTransaction` and `@BeforeEach`.** Lifecycle callbacks get a transaction of their own, rolled back
  before the test method starts. Seed from the test method.
- **First-level cache hides the second-level cache.** Two lookups in one session are answered by the
  persistence context; to prove an L2 hit, run each lookup in its own transaction
  (`QuarkusTransaction.requiringNew()`).
- **`Parameters` is deprecated** in this stream: pass a `Map<String, Object>` to `find`, `update`, `count`.
- **Envers and Flyway.** `validate` checks the audit tables and the `revinfo_seq` sequence too: write them in
  the migration with the `bdi-config-audit` naming (`_aud`, `rev`, `revtype`), increment 50 on the sequence.
- **`HHH90010101` on rollback.** Envers registers completion actions that a rolled-back test transaction never
  runs; Hibernate warns once per test. Harmless, and absent in production.

## Gaps and next steps

- Oracle and Db2 through `bdi-jpa-oracle` / `bdi-jpa-db2`: the same entities and sequences, stored procedures
  (`@NamedStoredProcedureQuery`), `default-schema`, and named persistence units (POC C, cookbook 4).
- Streaming a large result set (`stream()` with `statement-fetch-size`) and `@BatchSize` on collections, with
  measured statement counts.
- Panache's REST Data (`quarkus-hibernate-orm-rest-data-panache`) for CRUD endpoints generated from a
  repository: a candidate for the back-office tools, once the security POC defines who may call them.
