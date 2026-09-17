# Cookbooks

One page each, each backed by a POC (design note, section 6). A cookbook is written once the POC behind it
has been run on at least stage 1 of the target ladder, so that every claim in it is a test, not an opinion.

| # | Cookbook | Backed by | Status |
|---|---|---|---|
| 1 | Sizing the estate with MTA/MTR (Quarkus, EAP 8, OpenJDK targets) | estate assessment | planned, first |
| 2 | Target and packaging: VM vs Podman vs OCP; JVM vs native | POC A numbers | planned |
| 3 | Externalizing configuration: locations, profiles, `@ConfigMapping`, the contract schema | POC A, `concepts/platform-contract` | draft in [POC A README](../../pocs/a-jakarta-classic/README.md) and [platform walkthrough](../../pocs/a-jakarta-classic/platform/README.md) |
| 4 | Datasources and secrets (Agroal, Oracle/DB2, vault integration) | POC C | planned |
| 5 | JAX-RS / CDI / JPA from EAP | POC A | [05-jaxrs-cdi-jpa-from-eap.md](05-jaxrs-cdi-jpa-from-eap.md) |
| 6 | EJB / MDB / timers / XA recovery | POC B | planned |
| 7 | Remote EJB replacement (REST, gRPC, façade pattern, tx and identity propagation) | Remote EJB POC | planned |
| 8 | Queues/topics: Reactive Messaging vs JMS over AMQP 1.0; outbox and idempotency | Messaging POC | planned |
| 9 | Kafka: messaging vs Streams vs client; schema registry | Kafka POC | planned |
| 10 | SOAP with Quarkus CXF | POC C | planned |
| 11 | Spring compat vs rewrite | POC D | planned |
| 12 | Security: OIDC/RHBK, Elytron LDAP for legacy, mTLS via TLS registry, path policies, audit events | Security POC | planned |
| 13 | Observability: JSON logs to SIEM, Micrometer/Prometheus, OpenTelemetry tracing | POC A (JSON logs, health) then all | planned |
| 14 | AAP deploy and rollback on VMs (systemd) | POC A stage 1 | planned |
| 15 | Container on VM: Podman + Quadlet | POC A stage 2 | planned |
| 16 | Same app on OpenShift | POC A stage 3 | planned |
