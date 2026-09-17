# POC catalog

One proof of concept per migration class of the design notes (section 4), each delivered with the
split README (*what the developer wrote* / *what the platform provided*), its config contract, and
measured numbers. Every POC builds from the root parent POM, so all of them share one RHBQ stream.

| POC | Class (design note) | Directory | Status |
|---|---|---|---|
| A | Jakarta classic: JAX-RS, CDI, JPA, JTA, Bean Validation (4.1 A) | [`a-jakarta-classic`](a-jakarta-classic/) | first cut: code, tests, contract, conformance endpoint, stage-1 walkthrough, measured numbers |
| B | EJB-heavy: `@Stateless`/`@Singleton`/`@Schedule`, MDB, XA recovery (4.1 B) | `b-ejb-heavy` | planned |
| C | Legacy integration: JAX-WS (Quarkus CXF), Oracle/DB2 named datasources (4.1 C) | `c-legacy-integration` | planned |
| D | Spring WAR: `quarkus-spring-*` compatibility vs idiomatic rewrite (4.1 D) | `d-spring-war` | planned |
| E | Web UI: JSF/JSP decision cookbook (4.1 E) | `e-web-ui` | planned, only if the estate has it |
| Remote EJB | Replacement matrix: REST client, gRPC, façade, tx and identity propagation (4.2) | `remote-ejb-replacement` | planned |
| Messaging | Reactive Messaging vs JMS over AMQP 1.0; outbox and idempotent consumer (4.3) | `messaging-amqp` | planned |
| Kafka | messaging vs Streams vs client; Apicurio schema registry (4.4) | `kafka` | planned |
| Security | OIDC/RHBK inbound, token propagation outbound, TLS registry, audit events (4.5) | `security` | planned |

Per-POC deliverable checklist (design note 4.6):

- [ ] README split into *what the developer wrote* / *what the platform provided*
- [ ] config contract (`META-INF/config-contract.json`, kept in sync by a test)
- [ ] AAP job template deploying it on every stage of the ladder (VM + systemd, Podman + Quadlet, OpenShift)
- [ ] measured numbers: startup time, RSS, throughput, build time, config lines owned by the developer, deploy and rollback time
