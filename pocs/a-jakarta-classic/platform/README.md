# What the platform provides: stage 1 walkthrough

The other half of POC A. The developer built `target/quarkus-app/` once (see [../README.md](../README.md));
everything below is what the platform adds so that the same artifact runs in an environment. On a real
RHEL VM the AAP roles do all of it; this directory lets you replay it on a laptop, with the example files
standing in for what AAP renders.

| Platform concern (design note) | On a VM | In this directory |
|---|---|---|
| Rendered configuration (§2.2, §5) | `/etc/bdi/poc-a-jakarta-classic/application.properties` and `application-<env>.properties`, rendered from `group_vars/<env>/poc-a-jakarta-classic` | [`config/application.properties`](config/application.properties), [`config/application-uat.properties`](config/application-uat.properties) |
| Secrets (§2.2, §5) | `/etc/bdi/poc-a-jakarta-classic/secrets/secrets.properties`, 0600, written by the vault task (or a systemd credential) | [`secrets/secrets.properties`](secrets/secrets.properties) (sandbox database credentials) |
| Entry points (§2.2) | The unit sets `QUARKUS_CONFIG_LOCATIONS` and `QUARKUS_PROFILE`; nothing else is application-specific | [`run-stage1.sh`](run-stage1.sh) sets the same two variables |
| Runtime (§3, stage 1) | Red Hat build of OpenJDK 21, service user, directories, SELinux, firewalld (8080 to the LB, 9000 to ops), journald, hardened unit, standardized JVM flags | your JDK 21, `JAVA_OPTS` |
| Rolling update gate (§3) | `/q/health/ready` on port 9000, one host at a time behind the LB | `curl :9000/q/health/ready` |
| Conformance check (§3) | `/q/platform` on port 9000: version, active profile, source of every contract key | `curl :9000/q/platform` |
| Logs (§3) | JSON on stdout, captured by journald and shipped to the SIEM | JSON on your terminal |

## Replay it

```bash
# 1. the platform needs a PostgreSQL; any will do (the sandbox one, a container, the bank's)
#    the URL/user/password in config/ and secrets/ point at localhost:5432, database poc, user poc/poc
# 2. the artifact, built once by the developer
./mvnw package                                  # from the repository root, or: cd pocs/a-jakarta-classic && ../../mvnw package
# 3. run it like the systemd unit would
./platform/run-stage1.sh                        # QUARKUS_PROFILE defaults to uat
```

Then, from another terminal:

```bash
curl -s localhost:9000/q/health/ready | jq .          # readiness, includes the database check: the LB gate
curl -s localhost:9000/q/platform | jq .              # the conformance check, see below
curl -s localhost:8080/api/accounts | jq .
curl -s -X POST localhost:8080/api/accounts -H 'Content-Type: application/json' \
  -d '{"iban":"IT60X0542811101000000123456","holder":"Mario Rossi","initialBalance":2500.00}'
curl -s -X POST localhost:8080/api/accounts -H 'Content-Type: application/json' \
  -d '{"iban":"IT35X0100003200000000123456","holder":"Anna Bianchi","initialBalance":0.00}'
curl -s -X POST localhost:8080/api/transfers -H 'Content-Type: application/json' \
  -d '{"debtorIban":"IT60X0542811101000000123456","creditorIban":"IT35X0100003200000000123456","amount":1600.00,"reference":"rent"}'
# -> 422 "Amount exceeds the maximum of 1500.00 EUR": the uat limit rendered by the platform, not a code change
```

The database schema is created by Hibernate only in the inner loop (`%dev`/`%test`). In an environment the
DBA owns it, as on EAP: run `./mvnw verify` once against the target database, or apply the DDL your DBA
derives from the entities (cookbook 4 will bring Flyway for the classes where the team owns the schema).

## What the conformance check shows

`GET /q/platform` on the management port, as observed with the example files and `QUARKUS_PROFILE=uat`
(sources abbreviated):

```json
{
  "application": { "name": "poc-a-jakarta-classic", "version": "1.0.0-SNAPSHOT", "profiles": ["uat"] },
  "missing": [],
  "config": [
    { "key": "ledger.currency",              "value": "EUR",      "source": "DefaultValuesConfigSource" },
    { "key": "ledger.transfer.max-amount",   "value": "1500.00",  "source": "PropertiesConfigSource[source=file:.../config/application-uat.properties]" },
    { "key": "ledger.transfer.blocked-ibans","value": "IT66X0100503200000012345678", "source": "...application-uat.properties]" },
    { "key": "quarkus.datasource.jdbc.url",  "value": "jdbc:postgresql://127.0.0.1:5432/poc", "source": "...config/application.properties]" },
    { "key": "quarkus.datasource.password",  "value": "******",   "source": "...secrets/secrets.properties]", "secret": true },
    { "key": "quarkus.http.port",            "value": "8080",     "source": "ValueRegistryConfigSource" }
  ]
}
```

Three things ops can verify from it on every stage, without reading code: the version that is running, the
profile that is active, and that each key came from the file they rendered (and the password from the
vault, not from inventory). `missing` lists required keys nobody provided; with one of them the process does
not even start:

```
Configuration validation failed:
    java.util.NoSuchElementException: SRCFG00014: The config property ledger.transfer.max-amount is required but it could not be found in any config source
Process exited with non-zero status: 1
```

That is the behaviour the AAP pre-deploy validation front-runs by checking the rendered file against
`META-INF/config-contract.json` before touching a host.

## Rules of the config locations, as verified on RHBQ 3.33

- `QUARKUS_CONFIG_LOCATIONS` takes a comma-separated list of files or directories. A directory contributes its
  `application.properties` and, when a profile is active, its `application-<profile>.properties`. Files are
  contributed as they are, so the secrets file is listed explicitly.
- Values in these files override the ones inside the artifact; environment variables override both, which
  keeps `QUARKUS_DATASOURCE_*`-style variables available for per-instance values (§2.2).
- A listed location that does not exist is ignored: a missing rendered file surfaces as a missing key, which
  is why the conformance check exists.
- `QUARKUS_PROFILE` is a label plus the selector of the profile-aware file. The artifact carries no
  `%prod` or `%uat` keys (lint-enforced), so the profile changes nothing inside it.
- With the management interface enabled, health, OpenAPI and the conformance endpoint are on port 9000
  (`quarkus.management.port`); the API stays on 8080 (`quarkus.http.port`). Both are platform keys.

## Stage 2 and 3

Same artifact, same two variables. [`src/main/docker/Dockerfile.jvm`](../src/main/docker/Dockerfile.jvm)
builds the UBI 9 OpenJDK 21 image; the Quadlet unit or the Deployment mounts the rendered files at
`/etc/bdi/poc-a-jakarta-classic/` and sets `QUARKUS_CONFIG_LOCATIONS` and `QUARKUS_PROFILE`. Cookbooks 15
and 16 will carry the AAP roles for both.
