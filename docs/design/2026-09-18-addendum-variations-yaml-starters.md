# Addendum to the design notes, 18 September 2026

Decisions taken after the first cut of POC A. They refine the notes of 17 September; where the two differ,
this addendum wins.

## Banca d'Italia is a central bank, not "a bank": variations, not choices

Section 8 listed *Oracle/DB2? AMQ Broker, IBM MQ or Kafka? RHBK vs AD/LDAP? CyberArk or HashiCorp Vault?*
as open decisions. They are not decisions. The institution works like a public administration with several
technology stacks in use at once, so the platform has to serve each of them:

| Capability | Variations in the estate |
|---|---|
| Relational data | PostgreSQL, Oracle, Db2 |
| Queues and topics | AMQ Broker (AMQP 1.0), IBM MQ, Kafka / AMQ Streams |
| Identity | Red Hat build of Keycloak, Active Directory / LDAP |
| Secrets | CyberArk, HashiCorp Vault |

Consequences: the migration classes A to E stay, but each POC that touches one of these capabilities
shows the variation matrix rather than one path; the internal layer (§2.3) offers one starter per
variation on top of a shared config module; the config contract of an application says which variation
it needs (the keys differ per driver, broker or IdP), so AAP renders the right inventory.

## YAML everywhere

Configuration is YAML, never properties: the developer's `application.yaml`, the platform-rendered
`application-<env>.yaml`, the secrets file, the defaults of every config module. `bdi-config-core` brings
the YAML support to every application. Two rules learnt on RHBQ 3.33:

- quote amounts and anything with a decimal point (`max-amount: "1500.00"`): an unquoted `1500.00` is a
  YAML float and reaches the application as `1500.0`;
- one fully rendered file per environment, `application-<env>.yaml`, listed explicitly in
  `QUARKUS_CONFIG_LOCATIONS` with the secrets file. Profile-aware companion files
  (`application.yaml` + `application-uat.yaml` in one directory) are supported, but a key present in both
  is resolved from the base file, so the convention avoids them.

## Starters and config modules (refines §2.3)

Developers depend on capabilities (`bdi-rest-jackson`, `bdi-jpa-oracle`, `bdi-observability`, `bdi-test`),
never on Quarkus extension names. Each starter is an empty jar with an opinionated, RHBQ-supported
dependency set plus its config module; each config module ships standardized defaults as a config source
at ordinal 100 (below the developer's file and the platform's), contributes its platform keys to the
config contract, and hosts the shared runtime pieces (the conformance endpoint lives in
`bdi-config-observability`). The result for POC A: zero non-profile lines in the developer's
configuration. Details in `bdi-quarkus/README.md`.

## Still open

- Which variations each real application needs: the MTA/MTR pass on the estate answers it.
- Whether the config modules must become Quarkus extensions (build steps, enforcement) or stay libraries.
- The AAP roles and the contract validation task.
