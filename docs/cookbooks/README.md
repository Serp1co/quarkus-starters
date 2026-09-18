# Cookbooks

One page each, each backed by a POC (design note, section 6). A cookbook is written once the POC behind it
has been run on at least stage 1 of the target ladder, so that every claim in it is a test, not an opinion.

| # | Cookbook | Backed by | Status |
|---|---|---|---|
| 1 | Sizing the estate with MTA/MTR (Quarkus, EAP 8, OpenJDK targets) | estate assessment | planned, first |
| 2 | Target and packaging: VM vs Podman vs OCP; JVM vs native | POC A numbers | planned |
| 3 | Externalizing configuration: locations, profiles, `@ConfigMapping`, the contract schema, YAML rules | POC A, `concepts/platform-contract`, `bdi-quarkus` | draft in the [platform walkthrough](../../pocs/a-jakarta-classic/platform/README.md) and [bdi-quarkus/README.md](../../bdi-quarkus/README.md) |
| 4 | Datasources and secrets (Agroal, the `bdi-jpa-*` variations for PostgreSQL/Oracle/Db2, CyberArk and HashiCorp) | POC C | planned |
| 5 | JAX-RS / CDI / JPA from EAP | POC A | [05-jaxrs-cdi-jpa-from-eap.md](05-jaxrs-cdi-jpa-from-eap.md) |
| 6 | EJB / MDB / timers / XA recovery | POC B | draft in the [POC B README](../../pocs/b-ejb-heavy/README.md) (MDB with the messaging POC) |
| 7 | Remote EJB replacement (REST, gRPC, façade pattern, tx and identity propagation) | Remote EJB POC | draft in the [remote-ejb README](../../pocs/remote-ejb/README.md); LRA, token exchange, mTLS pending |
| 8 | Queues/topics: Reactive Messaging vs JMS over AMQP 1.0; outbox and idempotency | Messaging POC | draft in the [messaging-amqp README](../../pocs/messaging-amqp/README.md) |
| 9 | Kafka: messaging vs Streams vs client; schema registry | Kafka POC | draft in the [messaging-kafka README](../../pocs/messaging-kafka/README.md) |
| 10 | SOAP with Quarkus CXF | POC C | planned |
| 11 | Spring compat vs rewrite | POC D | planned |
| 12 | Security: Elytron LDAP for legacy, OIDC/RHBK, mTLS via TLS registry, path policies, audit events | Security POCs | draft in the [security-ldap](../../pocs/security-ldap/README.md) and [security-oidc](../../pocs/security-oidc/README.md) READMEs; outbound and mTLS pending |
| 13 | Observability: JSON logs to SIEM, Micrometer/Prometheus, OpenTelemetry tracing | POC A (JSON logs, health) then all | planned |
| 14 | AAP deploy and rollback on VMs (systemd) | POC A stage 1, [`ansible/`](../../ansible/README.md) | draft: the role and playbooks exist and run in the sandbox; host provisioning and a real systemd run pending |
| 15 | Container on VM: Podman + Quadlet | POC A stage 2 | planned |
| 16 | Same app on OpenShift | POC A stage 3 | planned |
| 17 | Data access: the EAP DAO vs Panache, sequences and batching, optimistic locking, second-level cache, Envers | Data POC | draft in the [data POC README](../../pocs/data-hibernate-panache/README.md) |
