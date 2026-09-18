# POC catalog

One proof of concept per migration class of the design notes (section 4), each delivered with the
split README (*what the developer wrote* / *what the platform provided*), its config contract, and
measured numbers. Every POC builds from the root parent POM, so all of them share one RHBQ stream.

| POC | Class (design note) | Directory | Status |
|---|---|---|---|
| A | Jakarta classic: JAX-RS, CDI, JPA, JTA, Bean Validation (4.1 A) | [`a-jakarta-classic`](a-jakarta-classic/) | on the `bdi-*` starters, YAML only: code, tests, contract, conformance endpoint, stage-1 walkthrough, measured numbers |
| B | EJB-heavy: `@Stateless`/`@Singleton`/`@Schedule`, `@TransactionAttribute`, `@Asynchronous`, XA recovery (4.1 B); MDB deferred to the messaging POC | [`b-ejb-heavy`](b-ejb-heavy/) | done: clustered Quartz timer on the database, Flyway, batch isolation, two-instance demo |
| C | Legacy integration: JAX-WS (Quarkus CXF), Oracle and Db2 through `bdi-jpa-oracle` / `bdi-jpa-db2`, named datasources (4.1 C) | `c-legacy-integration` | planned |
| D | Spring WAR: `quarkus-spring-*` compatibility vs idiomatic rewrite (4.1 D) | `d-spring-war` | planned |
| E | Web UI: JSF/JSP decision cookbook (4.1 E) | `e-web-ui` | planned, only if the estate has it |
| Remote EJB | Replacement matrix: REST client, gRPC, façade, tx and identity propagation (4.2) | `remote-ejb-replacement` | planned, last |
| Messaging | Reactive Messaging vs JMS over AMQP 1.0 on AMQ Broker; outbox, idempotent consumer, redelivery and DLQ (4.3) | [`messaging-amqp`](messaging-amqp/) | done; IBM MQ variation planned |
| Kafka | keyed records, ordering per partition, throttled commits, dead-letter topic, idempotent consumer (4.4) | [`messaging-kafka`](messaging-kafka/) | done; Streams and schema registry called out |
| Security | OIDC/RHBK and AD/LDAP inbound, token propagation outbound, TLS registry, audit events (4.5) | `security-oidc`, `security-ldap` | planned |

Per-POC deliverable checklist (design note 4.6):

- [ ] README split into *what the developer wrote* / *what the platform provided*
- [ ] config contract (`META-INF/config-contract.json`, kept in sync by a test)
- [ ] AAP job template deploying it on every stage of the ladder (VM + systemd, Podman + Quadlet, OpenShift)
- [ ] measured numbers: startup time, RSS, throughput, build time, config lines owned by the developer, deploy and rollback time
