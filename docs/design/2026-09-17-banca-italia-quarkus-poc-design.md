# Banca d'Italia — Quarkus POC & Cookbook Programme

Design notes · 17 September 2026 · Status: design phase, no code yet

---

## 1. Context and goal

- Role: Red Hat consultant engineer delivering POCs, cookbooks and examples.
- Current estate: Jakarta EE applications and Spring (Framework) WARs deployed on JBoss EAP.
- Objective: prove a valid, supportable alternative on Quarkus (Red Hat build of Quarkus, RHBQ).
- Constraint: Quarkus is **not** delivered on JBoss EAP — it needs its own runtime target.
- Core idea: relieve development teams of the configuration side of things; the platform, driven by Ansible Automation Platform (AAP), owns configuration and deployment.
- Framing for the ops audience: **today ops own `standalone.xml` and devs bind to JNDI names; tomorrow ops own the rendered `application-<env>.properties` and devs bind to logical config keys.** Everything below hangs off that.

---

## 2. Platform contract (confirmed decisions)

### 2.1 Build once, configure per environment — confirmed
- Packaging: **fast-jar** for VMs, **UBI OpenJDK image** for containers.
- The developer builds and writes once; deploying to VM, Podman or OCP/Kube is a platform concern.
- Environment-specific values never live in the artifact; `%prod.` keys banned by convention/lint (`%dev`/`%test` profiles allowed for the inner loop).

### 2.2 Config entry points — identical on every target — confirmed
- Configuration files at a fixed path, referenced via `QUARKUS_CONFIG_LOCATIONS`: rendered by AAP on VMs, mounted ConfigMap/Secret on OCP.
- Environment variables only for per-instance values; active profile via `QUARKUS_PROFILE`.
- Developers declare what they need through `@ConfigMapping` interfaces — that is the **config contract**, exportable as a key list/schema that AAP validates at deploy time.
- Secrets: one mount path across all stages (0600 file or systemd credential → podman secret → OCP Secret).

### 2.3 Internal BOM, parent POM and extensions — confirmed
- Internal BOM + parent POM pinned to the RHBQ platform stream.
- Internal extensions (`bdi-quarkus-observability`, `bdi-quarkus-security`, `bdi-quarkus-datasource`, …) inject bank-wide runtime defaults: JSON/syslog logging, health, Micrometer, OpenTelemetry, TLS registry, OIDC tenant conventions.
- This mechanism — not documentation — is what empties the developer's `application.properties`.

### 2.4 Target ladder — confirmed
1. **RHEL VM + systemd** (fast-jar) — first target; closest analogue to EAP-on-VM, where AAP shines.
2. **Container on VM, unorchestrated** — Podman + Quadlet on RHEL 9.
3. **Orchestrated** — OpenShift / Kubernetes.

The contract must hold unchanged across all three so the model is target-agnostic.

---

## 3. Target ladder — implementation notes

### Stage 1 — VM + systemd
- RHEL system roles provision: Red Hat build of OpenJDK, service user, directories, SELinux contexts, firewalld, journald/logrotate, hardened systemd unit, standardized JVM flags via a launcher script.
- App repositories never contain a unit file.
- Rolling update behind the load balancer gated on `/q/health/ready`.

### Stage 2 — Podman + Quadlet (validates the contract)
- Same UBI image as stage 3; same systemd unit + health-gated restart semantics as stage 1.
- Managed through `redhat.rhel_system_roles.podman` (Quadlet units, podman secrets, rootless users) and `containers.podman` (image pulls, registry logins).
- Config mounted at the same fixed path via `QUARKUS_CONFIG_LOCATIONS`; secrets as podman secrets at the same path.
- cgroup memory limits in the Quadlet unit + JVM `MaxRAMPercentage`, standardized by AAP.

### Stage 3 — OpenShift / Kubernetes
- Same inventory data applied as ConfigMap/Secret/Route/NetworkPolicy, either directly by AAP or committed to the GitOps repo — one source of truth for configuration.
- AAP builds nothing here; it orchestrates.

### Cross-stage invariants
- Logging: the app writes JSON to stdout in every stage. Stage 1 captures via journald/rsyslog, stage 2 via the podman journald log driver, stage 3 via OpenShift Logging.
- One deploy role with a `target` variable (`systemd-jar` | `quadlet` | `ocp`) fed by the same inventory data.
- One post-deploy conformance check run on every stage (health, version, config-source echo) so "same artifact, same contract" is a test, not a claim.

---

## 4. POC catalog

### 4.1 Migration classes — one POC each

| Class | Source (EAP) | Quarkus mapping | Gaps to state plainly |
|---|---|---|---|
| A. Jakarta classic | JAX-RS, CDI, JPA, JTA, Bean Validation | Quarkus REST, ArC, Hibernate ORM, Narayana | Near-mechanical; showcase dev mode, continuous testing, Dev Services |
| B. EJB-heavy | `@Stateless`/`@Singleton`/`@Schedule`, MDB | CDI + `@Transactional`, scheduler/Quartz, JMS (AMQ) or Reactive Messaging | No EJB container: no remote EJB, EAR, JCA, distributed HTTP sessions. XA recovery moves into the app: object store + unique `quarkus.transaction-manager.node-name` per instance become ops-owned config |
| C. Legacy integration | JAX-WS, Oracle/DB2 | Quarkus CXF (RHBQ-supported), Agroal named datasources | Where the bank actually lives; driver and native-image caveats |
| D. Spring WAR | Spring MVC/Data/Security on EAP | (1) `quarkus-spring-*` compatibility, (2) idiomatic rewrite | Compat is a bridge: no Boot autoconfiguration, annotation subset |
| E. Web UI (if present) | JSF/JSP | MyFaces (Quarkiverse, unsupported) vs Qute/SPA | A decision cookbook, not a "just works" story |

### 4.2 Remote EJB
No EJB container in Quarkus, so this POC is a **replacement matrix, not a shim**:

- Synchronous request/response → REST with MicroProfile REST Client (default); `quarkus-grpc` (supported) when a typed contract or streaming matters — the `.proto` takes the role of the remote interface.
- Fire-and-forget / batch → messaging (4.3, 4.4).
- Coexistence: a JAX-RS façade on the EAP side over the existing EJBs, so Quarkus apps call REST during the transition; EAP apps calling Quarkus need nothing special.
- The EJB client jars technically work from a plain JVM in JVM mode — document as unsupported, do not build it.
- Questions a bank will ask:
  - Transaction propagation across remote calls: EAP has it, Quarkus does not → local transaction + transactional outbox + idempotency; MicroProfile LRA (Narayana) for sagas — check its RHBQ support status.
  - Caller-identity propagation → OIDC token propagation or RHBK token exchange; mTLS for service identity.

### 4.3 Queues / topics (JMS/AMQP — Artemis mostly, broker-agnostic preferred)
Two axes: **dev API** and **wire protocol**. Agnosticism lives in the wire protocol (AMQP 1.0), not in JMS.

| Dev API | Extension | Fits |
|---|---|---|
| Reactive Messaging | `quarkus-messaging-amqp` (supported) | Default. `@Incoming`/`@Outgoing` channels; connector and destination live in properties → platform-owned, swappable with Kafka or the in-memory connector |
| JMS over AMQP 1.0 | `quarkus-qpid-jms` + `quarkus-pooled-jms` (pooling, XA) | Code that leans on JMS semantics: transacted sessions, selectors, request/reply on temporary queues, browsing |
| JMS, Artemis core protocol | `quarkus-artemis-jms` | Only for Artemis-specific features; locks to Artemis |
| JCA / MDB-shaped | `quarkus-ironjacamar` (Quarkiverse) | IBM MQ or other resource adapters; unsupported |

- Verify RHBQ support status of the Quarkiverse JMS extensions before committing.
- The real POC content is semantic: MDB + JPA in one XA transaction (EAP) versus Reactive Messaging ack strategies + transactional outbox + idempotent consumer. Demonstrate the failure modes — payments teams will want them.
- Address topology (anycast vs multicast on Artemis) is provisioned by AAP via `redhat.amq_broker`; the developer only names the channel.

### 4.4 Kafka
Pick by use case, not by default:

- `quarkus-messaging-kafka` (supported) — produce/consume, same channel abstraction as AMQP. It abstracts the API, not the semantics: partitions, ordering, consumer groups and commit strategies differ from queues; the cookbook must say so.
- `quarkus-kafka-streams` (supported) — only for stateful processing (joins, windows, aggregations). Operationally different: RocksDB `state.dir` must be persistent and node-local (AAP-managed directory in stages 1–2, PVC in stage 3), changelog/standby topics, rebalances.
- `quarkus-kafka-client` (supported) — admin operations, manual partition assignment, Kafka transactions / exactly-once.
- Schema: Avro + Apicurio Registry (Red Hat build) via `quarkus-apicurio-registry-avro`.
- Platform-owned keys: registry URL, SASL/OAUTHBEARER via RHBK, TLS, consumer-group naming.
- Dev Services for Kafka covers the inner loop.

### 4.5 Security
Scoped as inbound / outbound / transport / secrets / audit:

- **Inbound:** `quarkus-oidc` bearer and web-app flows against RHBK, multi-tenant. `@RolesAllowed` carries over unchanged. `web.xml` security constraints become `quarkus.http.auth.permission.*` path policies in properties — coarse authorization platform-owned, fine-grained stays in code. Legacy bridges: Elytron LDAP/JDBC, form auth, opaque-token introspection. Kerberos/SPNEGO is Quarkiverse-only — flag it if intranet SSO relies on it.
- **Outbound:** REST client OIDC client-credentials filter and token propagation; named TLS configurations for clients; SASL for Kafka/AMQP.
- **Transport:** TLS registry (`quarkus.tls.<name>.*`), mTLS with certificate-to-role mapping, `reload-period` so AAP-rotated certificates are picked up without restart.
- **Secrets:** one mount path across stages; SmallRye encrypted values (`aes-gcm-nopadding` handler, key delivered via systemd credential) as a rendered-inventory option; `quarkus-vault` or a custom ConfigSource for CyberArk — check support.
- **Audit:** CDI security events (authentication/authorization success and failure) → JSON log → SIEM. FIPS mode on RHEL: fine in JVM mode, caveats in native.

### 4.6 Deliverable per POC
- README split into *what the developer wrote* / *what the platform provided*.
- The config contract (keys/schema).
- The AAP job template that deploys it, on every stage of the ladder.
- Measured numbers: startup time, RSS, throughput, build time, config lines owned by the developer (target ≈ 0), deploy and rollback time.

---

## 5. What AAP owns

- **Runtime provisioning** (RHEL system roles): JDK, service user, directories, SELinux, firewalld, journald/logrotate, hardened systemd unit, standardized JVM flags. Podman/Quadlet layer for stage 2.
- **Config rendering:** `group_vars/<env>/<app>` → properties file at the fixed path; profile via `QUARKUS_PROFILE`; contract validation before deploy.
- **Secrets:** pulled at deploy time from the bank's vault (CyberArk / HashiCorp) into a service-user-only file, systemd credential or podman secret — never into inventory.
- **Deploy workflow:** versioned artifact from Nexus/Artifactory → `serial` rolling update behind the LB gated on `/q/health/ready` → smoke/conformance test → rollback by flipping to the previous release. Survey for version, approval node for prod, check-mode drift detection.
- **Messaging topology:** broker addresses/queues via `redhat.amq_broker`; Kafka topics/ACLs as platform-owned resources.
- **Collections:** `redhat.rhel_system_roles`, `ansible.posix`, `containers.podman`, `kubernetes.core` / `redhat.openshift`, the vault collection, `redhat.amq_broker`, and `middleware_automation.quarkus` as a starting point. The latter covers checkout, Maven build and deployment as a systemd service, but it is a community project without Red Hat support (Quarkus and Ansible themselves are supported products). Unlike `redhat.eap`, expect to fork/wrap it into a bank-owned collection packaged in a custom Execution Environment.
- **OpenShift variant:** same inventory data applied as ConfigMap/Secret/Route/NetworkPolicy, or committed to the GitOps repo.

---

## 6. Cookbook set (one page each, each backed by a POC)

1. Sizing the estate with MTA/MTR (Quarkus, EAP 8, OpenJDK targets) — first, so classes A–E mirror real applications
2. Target & packaging: VM vs Podman vs OCP; JVM vs native (native only with a measured reason)
3. Externalizing configuration: locations, profiles, `@ConfigMapping`, the contract schema
4. Datasources & secrets (Agroal, Oracle/DB2, vault integration)
5. JAX-RS / CDI / JPA from EAP
6. EJB / MDB / timers / XA recovery
7. Remote EJB replacement (REST, gRPC, façade pattern, tx and identity propagation)
8. Queues/topics: Reactive Messaging vs JMS over AMQP 1.0; outbox and idempotency
9. Kafka: messaging vs Streams vs client; schema registry
10. SOAP with Quarkus CXF
11. Spring compat vs rewrite
12. Security: OIDC/RHBK, Elytron LDAP for legacy, mTLS via TLS registry, path policies, audit events
13. Observability: JSON logs to SIEM, Micrometer/Prometheus, OpenTelemetry tracing
14. AAP deploy & rollback on VMs (systemd)
15. Container on VM: Podman + Quadlet
16. Same app on OpenShift

---

## 7. Credibility guardrails

- Put EAP 8 (bootable jar / OCP via `redhat.eap`) in the comparison. "Quarkus for the right classes, EAP 8 for the rest" is more believable than "migrate everything".
- Tag every extension used as RHBQ-supported vs Quarkiverse; a central bank will ask.
- Confirm the RHBQ entitlement (Red Hat Application Foundations vs what the existing EAP subscription actually carries) before promising support.
- Run MTA/MTR against the real estate before choosing POC applications; toy apps prove nothing.

---

## 8. Open decisions

Resolved:
- Packaging: fast-jar (VM), UBI image (containers).
- Build once / deploy anywhere.
- Internal BOM + extensions.
- Target ladder: VM + systemd → Podman/Quadlet → OCP/Kube.

Still open (they change the design):
1. Stack specifics: Oracle/DB2? AMQ Broker, IBM MQ or Kafka for which flows? RHBK vs AD/LDAP? CyberArk or HashiCorp Vault?
2. Stage-1 broker: AMQ Broker on RHEL?
3. Kafka distribution: AMQ Streams (RHEL or OCP) vs an existing cluster.
4. Confirmation that RHBK is the IdP.
5. JDK baseline (17/21) and any real interest in native.
6. Who edits inventory data — ops only, or developers via PR with ops approval?
7. Has MTA/MTR already been run on the estate?

---

## 9. Verify before committing (support / entitlement checklist)

- `middleware_automation.quarkus` — community, no Red Hat support.
- `quarkus-qpid-jms`, `quarkus-pooled-jms`, `quarkus-artemis-jms` — RHBQ support status.
- `quarkus-ironjacamar`, MyFaces, Kerberos/SPNEGO — Quarkiverse, unsupported.
- `quarkus-narayana-lra` (MicroProfile LRA) — RHBQ support status.
- `quarkus-vault` — RHBQ support status.
- Quarkus CXF, `quarkus-messaging-amqp`, `quarkus-messaging-kafka`, `quarkus-kafka-streams`, `quarkus-kafka-client`, `quarkus-apicurio-registry-avro`, `quarkus-grpc` — confirm against the current RHBQ supported-extensions list for the chosen stream.
- RHBQ entitlement under the bank's subscriptions.
