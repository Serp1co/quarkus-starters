# Quarkus Starters

Container repository for the **Banca d'Italia Quarkus POC and cookbook programme**: the proofs of
concept, the one-page cookbooks and the platform concepts that show how the Jakarta EE and Spring
applications running on JBoss EAP move to the **Red Hat build of Quarkus (RHBQ)**, with the platform
(Ansible Automation Platform, AAP) owning configuration and deployment.

The framing for the ops audience, from the design notes: *today ops own `standalone.xml` and developers
bind to JNDI names; tomorrow ops own the rendered `application-<env>.yaml` and developers bind to
logical config keys.* Everything in this repository hangs off that sentence.

Source of truth: [docs/design/2026-09-17-banca-italia-quarkus-poc-design.md](docs/design/2026-09-17-banca-italia-quarkus-poc-design.md).
Section numbers quoted below (§2.1, §4.6, ...) refer to it.

## Layout

| Path | Role |
|---|---|
| [`pom.xml`](pom.xml) | Parent POM and aggregator. Plays the *internal parent POM* of §2.3: pins every module to one RHBQ stream and centralises build conventions. |
| [`bdi-quarkus/`](bdi-quarkus/) | The bank-owned layer of §2.3: the BOM, the opinionated **starters** developers depend on instead of Quarkus extensions (`bdi-rest-jackson`, `bdi-jpa-postgresql`/`-oracle`/`-db2`, `bdi-observability`, `bdi-test`) and the **config modules** that configure them in a standardized way. |
| [`concepts/`](concepts/) | Platform concepts shared by the POCs, as code. [`platform-contract`](concepts/platform-contract/): config contract export, config-source echo, artifact lint (§2.1, §2.2). |
| [`pocs/`](pocs/) | One POC per migration class (§4). Catalog and status in [pocs/README.md](pocs/README.md). |
| [`docs/cookbooks/`](docs/cookbooks/) | One page per cookbook (§6). Index and status in [docs/cookbooks/README.md](docs/cookbooks/README.md). |
| [`docs/design/`](docs/design/) | Design notes and the [addendum of 18 September](docs/design/2026-09-18-addendum-variations-yaml-starters.md) (variations, YAML, starters). |
| [`.github/workflows/build.yml`](.github/workflows/build.yml) | CI: builds and tests every module against RHBQ. |

## Platform stream

| | |
|---|---|
| Stream | RHBQ **3.33** (LTS), the latest stream published by [code.quarkus.redhat.com](https://code.quarkus.redhat.com/) |
| Platform BOM | `com.redhat.quarkus.platform:quarkus-bom:3.33.1.redhat-00006` |
| Maven plugin | `com.redhat.quarkus.platform:quarkus-maven-plugin`, same version |
| Repository | `https://maven.repository.redhat.com/ga` (declared in the root POM, `rhbq` profile, active by default) |
| JDK | Red Hat build of OpenJDK **21** (`maven.compiler.release`); RHBQ 3.33 supports 17, 21 and 25 |
| Maven | 3.9.12 through the wrapper (`./mvnw`) |

The stream is chosen in exactly one place, the `quarkus.platform.*` properties of the root POM. To move
to a newer stream, read the platform version from code.quarkus.redhat.com (stream selector, or
`curl -s https://code.quarkus.redhat.com/api/streams`) and update `quarkus.platform.version` together
with `quarkus.upstream.version`.

In the bank, Nexus/Artifactory mirrors the Red Hat GA repository; a `<mirror>` in `settings.xml`
overrides the URL without touching the POM.

**Escape hatch.** `./mvnw verify -Dcommunity=true` builds against the upstream community
platform the RHBQ stream is based on (`io.quarkus.platform:quarkus-bom:3.33.1`), for machines that
cannot reach the Red Hat repository. Never for a deliverable: the support contract covers RHBQ bits only.

## Build and run

```bash
./mvnw verify                                   # every module, unit + @QuarkusTest tests
./mvnw verify -DskipITs=false                   # also the @QuarkusIntegrationTest classes against the packaged fast-jar
./mvnw quarkus:dev -pl pocs/a-jakarta-classic   # dev mode: live reload, continuous testing, Dev UI, Dev Services
```

Tests use **Dev Services**: Quarkus starts PostgreSQL in a container (Podman or Docker) with nothing to
configure. Without a container runtime, point the tests at any PostgreSQL through the same keys the
platform renders in production, and Dev Services stay out of the way:

```bash
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/poc
export QUARKUS_DATASOURCE_USERNAME=poc QUARKUS_DATASOURCE_PASSWORD=poc
./mvnw verify
```

## Conventions

- **Variations, not choices.** Banca d'Italia runs several stacks side by side (PostgreSQL, Oracle, Db2;
  AMQ Broker, IBM MQ, Kafka; RHBK, AD/LDAP; CyberArk, HashiCorp). Every capability is a family of starters
  sharing one config module; an application picks the member its estate needs.
- **Starters, not extensions (§2.3).** Applications depend on `bdi-*` starters from `bdi-quarkus/`; the
  config modules behind them inject standardized defaults (ordinal 100, always overridable) and their
  platform keys into the config contract.
- **YAML, never properties.** `application.yaml` in the artifact, `application-<env>.yaml` rendered by the
  platform, YAML secrets files, YAML defaults in the config modules. Quote amounts (`"1500.00"`).
- **Build once, configure per environment (§2.1).** Packaging is fast-jar for VMs and the UBI OpenJDK
  image for containers; the same artifact goes to every stage. A `"%prod":` section (or any environment
  name) is banned from `application.yaml`; `ArtifactConfigLint` fails the build on it. Only `%dev`/`%test`
  inner-loop values live in the artifact.
- **Config entry points (§2.2).** One fully rendered `application-<env>.yaml` plus the secrets file at a
  fixed path, both named in `QUARKUS_CONFIG_LOCATIONS`; the active profile through `QUARKUS_PROFILE`;
  environment variables only for per-instance values. What the platform must render is declared in
  `@ConfigMapping` interfaces and exported as `META-INF/config-contract.json` by each application.
- **No unit files, no environment in images (§3).** Application repositories never contain a systemd unit;
  the Dockerfile carries no configuration. Both belong to the AAP roles.
- **Support tags (§7).** Every extension a POC uses is tagged *RHBQ-supported* or *Quarkiverse* in its
  README; re-verify against the RHBQ supported-configurations article when the stream changes (§9).
- **Coordinates.** Group `it.bancaditalia.quarkus`, packages `it.bancaditalia.quarkus.<poc|platform>...`,
  internal artifacts follow the `bdi-quarkus-*` naming of §2.3 when they appear.

## Status

| Item | State |
|---|---|
| Container project (parent POM, wrapper, CI, layout) | done |
| `bdi-quarkus`: BOM, 6 starters, 4 config modules | done; messaging, security, secrets, SOAP and Spring variations planned with their POCs |
| Concept `platform-contract` | done, unit-tested |
| POC A, Jakarta classic | on the starters, YAML only, zero non-profile config lines; tests, contract, conformance endpoint, stage-1 walkthrough, measured numbers |
| Cookbook 5 (JAX-RS / CDI / JPA from EAP) | draft |
| Other POCs and cookbooks | planned, see the indexes |
