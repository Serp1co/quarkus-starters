# Cookbook 5: JAX-RS / CDI / JPA from EAP

Backed by [POC A](../../pocs/a-jakarta-classic/). Status: draft, verified on RHBQ 3.33 (JVM mode,
fast-jar, PostgreSQL).

## When it applies

Migration class A of the design notes (§4.1): applications whose EAP footprint is JAX-RS, CDI, JPA, JTA and
Bean Validation, and little else. No remote EJB, no MDB, no JCA, no JSF. MTA/MTR (cookbook 1) reports them
with a handful of mandatory issues, all in the deployment descriptors. Everything with `@Stateless`,
`@Schedule` or a resource adapter goes to cookbook 6 first.

## The mapping

| On EAP | On RHBQ 3.33 | What changes in the code |
|---|---|---|
| JAX-RS (RESTEasy) | Quarkus REST, through the `bdi-rest-jackson` starter | Nothing: `@Path`, `@GET`, `@Valid` parameters, `@Provider ExceptionMapper`, `UriInfo` all run as they are. Resources returning plain objects run on a worker thread, so blocking JPA calls are fine. |
| CDI (Weld) | ArC (comes with every starter) | Nothing for beans. `beans.xml` goes away (bean discovery is build-time). Portable extensions do not exist; the rare application that has one gets a build step instead. |
| JPA (Hibernate ORM) | Hibernate ORM 7, through the `bdi-jpa-postgresql`, `bdi-jpa-oracle` or `bdi-jpa-db2` starter (same code, the driver differs) | Entities, named queries, `@Version` unchanged. `persistence.xml` goes away; the persistence unit is the default one and the datasource is a set of platform keys. `@PersistenceContext` still works, `@Inject EntityManager` is the CDI spelling. |
| JTA (Narayana) | Narayana (in every `bdi-jpa-*` starter) | `@Transactional` (jakarta.transaction) on CDI beans, REQUIRED by default. An unchecked exception rolls back, as `@ApplicationException(rollback = true)` did; use `@Transactional(rollbackOn = ...)` for checked ones. |
| Bean Validation | Hibernate Validator (in `bdi-rest-jackson`) | Nothing: constraints, custom `ConstraintValidator`s, `@Valid` on REST parameters. A violation answers 400 with a JSON report, no mapper needed. Constraints on `@ConfigMapping` methods are validated at start-up. |
| `@Stateless` service bean | `@ApplicationScoped` + `@Transactional` | The only edit in the service layer. `@Singleton` maps to `@ApplicationScoped` too; `@Startup` initialisation becomes an observer of `StartupEvent`. |
| Datasource in `standalone.xml`, JNDI lookup in code | `quarkus.datasource.*` rendered by the platform | The code stops naming JNDI resources; the driver on the classpath is the one build-time choice. |
| System properties and JNDI env entries | one `@ConfigMapping` interface | The developer declares keys with defaults where honest; the platform renders the rest. The interface is the contract (cookbook 3). |
| `web.xml` security constraints | `quarkus.http.auth.permission.*` (cookbook 12) | Platform-owned path policies; `@RolesAllowed` stays in code. |
| `jboss-web.xml` context root | `quarkus.http.root-path`, or the `@Path` prefix | Platform-owned if it must vary per environment. |
| WAR deployed by ops | fast-jar (`target/quarkus-app/`) deployed by AAP | `java -jar quarkus-run.jar`; the unit, JVM flags and paths belong to the platform roles. |

## Step by step

1. Run MTA/MTR with the Quarkus target on the application; keep the report next to the migration branch.
2. Create the module from the root parent POM (or import `bdi-quarkus-bom`): the platform stream, the
   Maven plugin, surefire/failsafe and the enforcer come from there. Add the starters: `bdi-rest-jackson`,
   the `bdi-jpa-*` variation of the application's database, `bdi-observability`, `bdi-test`. Every extension
   behind them is RHBQ-supported, and their config modules set the standardized defaults.
3. Delete `beans.xml`, `persistence.xml`, `web.xml`, `jboss-web.xml`, `jboss-deployment-structure.xml`.
   For each deleted line, decide: gone, code, standardized default of a config module, or platform key.
4. Replace `@Stateless`/`@Singleton` with `@ApplicationScoped` and put `@Transactional` on the methods that
   were container-managed transactions.
5. Move every system-property or JNDI lookup into a `@ConfigMapping` interface; add `@Doc` for the ops
   reader and `@Secret` on vault-delivered keys. That is the whole contract: the build derives
   `META-INF/config-contract.json` from the mapping and the config modules in use (cookbook 3,
   `concepts/platform-contract`).
6. Leave entities, repositories, resources, DTOs and validators alone.
7. Tests: `@QuarkusTest` + RestAssured for the API, `@Inject` the services for transaction semantics,
   `@QuarkusIntegrationTest` for the packaged artifact. Keep a `"%test":` section in `application.yaml` for
   what the inner loop needs, nothing else.
8. Run `./mvnw quarkus:dev`: Dev Services start the database, continuous testing runs the suite on save,
   the Dev UI shows the effective configuration.
9. Hand over the fast-jar (the derived `META-INF/config-contract.json` is inside it); the platform does the rest (POC A's
   [platform walkthrough](../../pocs/a-jakarta-classic/platform/README.md)).

## Things that bit, so you do not have to find them again

- Jakarta REST 3.1 (the level in RHBQ 3.33) has no `Response.Status` constant for 422; use
  `Response.status(422, "Unprocessable Entity")`.
- Since Quarkus 3.31 the test artifact is `io.quarkus:quarkus-junit`; `quarkus-junit5` is a relocation
  that warns at every build. Older EAP-era templates use the old name.
- `@QuarkusIntegrationTest` launches the artifact with `-Dquarkus.profile=prod`, exactly as an environment
  does, so `%test` keys do not apply and required contract keys must be supplied. POC A does it with a
  `QuarkusTestResourceLifecycleManager` that renders a YAML file and passes `quarkus.config.locations`,
  which is also the best possible demonstration of the contract.
- Enabling the management interface moves every `/q/*` endpoint (health, OpenAPI, the conformance check)
  to port 9000. Point the load balancer probes and the API-catalogue import there, or set
  `quarkus.smallrye-openapi.management.enabled=false` to keep the OpenAPI document on the API port.
- Lazy associations read outside the transaction that loaded them: keep the `join fetch` in the query (as
  the EAP code probably already did) rather than relying on an open session.
- Hibernate's schema generation key is `quarkus.hibernate-orm.schema-management.strategy` on this stream
  (`database.generation` is the deprecated spelling); `bdi-config-jpa` sets it for `%dev`/`%test` only.
- YAML: quote decimals (`"1000.00"`), otherwise they are floats and arrive as `1000.0`. Lists
  (`blocked-ibans: [ ... ]`) bind to `List<String>` and echo as a comma-joined value.
- Profile-aware YAML companions (`application.yaml` + `application-uat.yaml` in one directory) resolve a
  key present in both from the base file. The platform renders one file per environment instead.
- Two config sources at the same ordinal with the same key resolve unpredictably: config modules never
  overlap in the keys they default.
