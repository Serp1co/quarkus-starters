# POC B: EJB-heavy on the Red Hat build of Quarkus

Migration class B of the design notes (§4.1): an application whose EAP footprint is **session beans, singletons,
timers, transaction attributes, asynchronous methods and XA**, moved to RHBQ 3.33 without an EJB container.
MDBs are deliberately left to the messaging POC (§4.3): they need a broker, and the answer there is a
replacement matrix, not a shim. Status: all tests green in JVM mode against PostgreSQL, two-instance cluster
demo replayed with rendered configuration; no AAP role run on a real host yet.

The application is a **settlement batch**: payment instructions are submitted through REST and settled by a
timer that runs once per interval across all instances, in transactional batches, with a reference-data
singleton loaded at start-up and failures notified asynchronously.

| Method | Path | What it does |
|---|---|---|
| POST | `/api/instructions` | submit an instruction (Bean Validation) |
| GET | `/api/instructions`, `/api/instructions/{id}` | read |
| POST | `/api/settlement/runs` | run the settlement now (the operator's button) |
| GET | `/api/settlement/runs` | the runs: which node, how many settled, rejected, failed batches |
| GET | `/api/settlement/notifications` | what the asynchronous notifier sent |
| GET | `/api/settlement/cluster` | the Quartz cluster as the database sees it: live instances, persisted jobs |
| GET | `/api/settlement/rates/{ccy}` | the start-up singleton's reference data |

## The EJB constructs, one by one

| On EAP | Here | Where |
|---|---|---|
| `@Stateless` service | `@ApplicationScoped` + `@Transactional` | `InstructionService` |
| `@Singleton @Startup @Lock(READ)` with `@Lock(WRITE)` on the refresh | `@ApplicationScoped`, `@Observes StartupEvent`, ArC `@Lock(READ/WRITE)` | `ReferenceRates` |
| `@Schedule(persistent = true)` + HA-singleton policy | `@Scheduled(every = "{settlement.interval}", identity = ...)` on Quartz with a **clustered JDBC store** (`bdi-scheduler`): the trigger is a row in the application database, one node acquires each fire, a dead node's misfire is recovered by the others | `SettlementTimer` |
| `@TransactionAttribute(REQUIRES_NEW)` per batch | `@Transactional(REQUIRES_NEW)`; a failing batch rolls back alone, the run and the other batches commit | `SettlementBatch`, `SettlementService` |
| `@Asynchronous` | MicroProfile `ManagedExecutor.runAsync` with context propagation | `FailureNotifier` |
| `SELECT ... FOR UPDATE` in the batch | `LockModeType.PESSIMISTIC_WRITE` | `InstructionRepository` |
| XA recovery: `standalone/data/tx-object-store`, the server name as node id | `quarkus.transaction-manager.enable-recovery` (on by `bdi-config-jpa`), `node-name` and `object-store.directory` as **platform keys per instance**, rendered from the instance fragment; the object store lives under `/var/lib/bdi/<app>`, outside the release directory | contract, `ansible/fragments/instance.yaml` |
| Timer and application schema | Flyway (`bdi-flyway-postgresql`): `V1__quartz.sql` (the Quartz DDL of the 2.5.2 distribution), `V2__settlement.sql`; Hibernate runs in `validate` mode | `src/main/resources/db/migration` |
| MDB | not here: the messaging POC (Reactive Messaging vs JMS over AMQP 1.0, outbox, idempotent consumer) | |

Two lessons that only show up when you run it:

- **REQUIRES_NEW and foreign keys.** The first version wrote the run row in an outer REQUIRED transaction and let
  each REQUIRES_NEW batch reference it: every batch failed with a foreign-key violation, because the batch
  transaction cannot see the uncommitted parent row. The run row is now committed in its own transaction before
  the batches, and the totals in another one after them. EAP code with the same shape has the same bug; it is
  just less visible when the container hides the transaction boundaries.
- **Build-time keys.** `quarkus.quartz.clustered` is fixed at build time: the conformance endpoint reports
  `BuildTime RunTime Fixed` as its source, not the module that defaulted it. It cannot be changed by the platform,
  which is the right property for "is this artifact cluster-safe".

## What the developer wrote

`src/main/java/it/bancaditalia/quarkus/poc/ejb`: `domain` (two entities, one exception), `repository`, `service`
(instruction, batch, settlement, notifier), `timer`, `reference`, `api`, `config` (`SettlementConfig`, the
`@ConfigMapping`; the contract is derived from it). `application.yaml` holds `%dev`/`%test` values only; the
starters are `bdi-rest-jackson`, `bdi-jpa-postgresql`, `bdi-jpa-xa` (the EAP `xa-data-source`: XA fixed at
build time, recovery node name and object store rendered at runtime), `bdi-flyway-postgresql`, `bdi-scheduler`,
`bdi-observability`, `bdi-test`.

Tests: `SettlementServiceTest` (batch isolation: settled, rejected, and the failing batch left pending; the
asynchronous notification), `SettlementTimerTest` (jobs in `qrtz_job_details`, this instance in
`qrtz_scheduler_state`, the timer firing within 15 s from the 2 s test interval), `InstructionResourceTest`,
`ConformanceEndpointTest` (the scheduler and Flyway defaults traced to their modules, the derived contract
carrying the datasource, XA and Flyway keys), `ArtifactConfigLintTest`, and the integration test on the packaged artifact.

## Config contract

`META-INF/config-contract.json`, derived at build time: application keys first (`settlement.*`, from the
`@ConfigMapping`), then the platform keys declared by the descriptors of the config modules in use: datasource, XA node name and object store (`bdi-config-jpa`), Quartz cluster
mode and scheduler switch (`bdi-config-scheduler`), migrate-at-start (`bdi-config-flyway`), ports and log level.

## Run it

```bash
./mvnw quarkus:dev -pl pocs/b-ejb-heavy          # Dev Services PostgreSQL, Flyway migrates, the timer runs every 10 s
./mvnw verify -DskipITs=false                     # tests + integration tests
./mvnw package && pocs/b-ejb-heavy/platform/run-cluster-demo.sh   # two instances, one database (needs PostgreSQL at localhost:5432, poc/poc)
```

The cluster demo starts two instances from the same artifact with one environment file, one instance file
each (`platform/config/instance-node-a.yaml`, `instance-node-b.yaml`: node name, XA object store, ports) and
the secrets file, then submits six instructions and watches the timer for 26 s. Observed in the sandbox:

```
{"instances":["vm1789741829641","vm1789741829900"],"jobs":2}
node names: node-a / node-b
14:30:31 node-b settled=...   14:30:36 node-b   14:30:41 node-b   14:30:46 node-b   14:30:51 node-b   14:30:56 node-b
```

One run per 5 s interval, never two for the same fire; Quartz lets one node keep the lock while it is healthy,
the other takes over when it stops. Kill one instance mid-demo to see it.

## Precedence, as measured (`ordinal` in `/q/platform`)

| Source | Ordinal | Note |
|---|---|---|
| build-time fixed keys | max | cannot change after the build (`quarkus.quartz.clustered`) |
| system properties | 400 | |
| files in `QUARKUS_CONFIG_LOCATIONS` | 300 | the **first listed wins**; they beat environment variables at the same ordinal |
| environment variables | 300 | lose to the location files: per-instance values are rendered, not passed as env |
| `application.yaml` in the artifact | 250 | `%dev`/`%test` only |
| `BdiDefaults[bdi-config-*]` | 100 (110 for `bdi-config-flyway`) | |

Hence the platform convention: `QUARKUS_CONFIG_LOCATIONS=secrets.yaml,instance.yaml,application-<env>.yaml`.

## Measured numbers

| Metric | Value |
|---|---|
| Start-up to ready, prod profile (Flyway validate, Quartz JDBC store) | about 3 s |
| Test suite | 9 tests + 3 integration tests, green |
| Config lines owned by the developer (non-profile) | 0 |
| Timer accuracy across two instances | one run per 5 s interval over 26 s, no double execution |

## Gaps and next steps

- MDB and JMS: the messaging POC, with AMQ Broker (AMQP 1.0) and IBM MQ variations.
- XA with two resources (database + broker) and a real recovery test (kill during prepare): needs the broker.
- Flyway for Oracle (`bdi-flyway-oracle`) and Db2 (the Flyway Db2 module is not in the platform BOM: to check).
- `@Schedule` with calendars and `@Timeout` programmatic timers: `Scheduler.newJob()` on Quarkus, not covered.
