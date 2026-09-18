# Concept: platform contract

The developer/platform contract of design notes §2.1 and §2.2, **derived, never authored**. Nobody writes a
contract per application: the platform's parent POM runs `ContractExporter` on every application after
compilation, and the result (`META-INF/config-contract.json`, inside the artifact) is what the deploy role
validates the rendered files against and what the conformance endpoint resolves at runtime.

| Piece | What it does |
|---|---|
| `ContractExporter` | Scans the compiled classes for `@ConfigMapping` interfaces (keys, defaults, `@Doc`, `@Secret`), messaging channels (`@Incoming`, `@Outgoing`, `@Channel`) and the roles named in `@RolesAllowed`, merges the `PlatformDescriptor` of every `bdi-config-*` module on the classpath, writes the JSON. Bound in the root POM's `bdi-application` profile (exec-maven-plugin, phase `process-classes`), which activates by itself on any module that ships `src/main/resources/application.yaml`: applications get it, libraries never do, nobody declares it. `-Dbdi.contract.skip=true` is the escape hatch. |
| `PlatformDescriptor` | What a config module declares in its `META-INF/bdi-contract-platform.json`: the Quarkus keys of the extensions it configures, and key templates for the channels an application declares (`{channel}`, `{application}` substituted). Written once per module by the platform team, never per application. |
| `ConfigContract` | The keys, JSON in and out, and the runtime echo (`echo(config)`: value, source and ordinal of every key, secrets masked; `missing(config)`). |
| `@Doc`, `@Secret` | Optional annotations on `@ConfigMapping` methods: the description ops read, and the vault marker. |
| `ArtifactConfigLint` | The build-time enforcement of "environment configuration belongs to the platform": run by the parent POM on every application, it reads every packaged configuration resource (`application.yaml/.yml/.properties`, profile files, `META-INF/microprofile-config.properties`) against the derived contract and fails the build on an environment profile, a packaged secret (contract or name-based), or a platform-owned runtime key fixed in the artifact. Build-time keys and inner-loop (`%dev`, `%test`) values pass. |

## Who owns what

- The **developer** writes code: a `@ConfigMapping` interface for what the application needs, channel names
  in `@Incoming`/`@Outgoing`. That is all; the contract follows from it.
- The **platform** owns the descriptors of its config modules, the exporter binding in the parent POM, the
  deploy-time validation (Ansible reads the JSON out of the artifact) and the conformance check.

## Contract JSON

```json
{ "keys": [
    {"name": "ledger.transfer.max-amount", "type": "BigDecimal", "required": true, "default": null, "secret": false, "owner": "application", "doc": "..."},
    {"name": "mp.messaging.incoming.orders-in.address", "type": "String", "required": false, "default": "orders-in", "secret": false, "owner": "platform", "doc": "..."},
    {"name": "quarkus.datasource.password", "type": "String", "required": true, "default": null, "secret": true, "owner": "platform", "doc": "..."}
] }
```

Every key carries a **phase**: `runtime` (the platform renders it per environment) or `build-time` (fixed
when the artifact is built: `quarkus.datasource.jdbc.transactions`, `quarkus.quartz.clustered`, pool metrics).
Rendering a build-time key changes nothing, so the pre-deploy check refuses it (`fixed`) and the runtime
refuses a mismatching value outright (`quarkus.config.build-time-mismatch-at-runtime=fail`, a default of
`bdi-config-core`). Changing a build-time choice means another starter: XA is `bdi-jpa-xa`, whose descriptor
`replaces` the key of `bdi-config-jpa` with default `xa`, and an `xa-data-source` fragment is checked against
that, never rendered.

Keys also carry what a value must satisfy: the `type` (converted like SmallRye does: `int`, `boolean`,
`Duration`, `BigDecimal`, enums as `values`), `min`/`max`, a `pattern`, and `required-if` (`other.key=value`,
e.g. the OIDC client secret when the application type is `web-app`). Bean Validation on a `@ConfigMapping`
method (`@Min`, `@Max`, `@Pattern`, an enum return type) becomes the constraint of the application key.
Wildcard keys (`quarkus.datasource.*.password`) describe a family: a rendered key matching a secret family is
`misplaced` if it is not in the vault file, and every match is type-checked. The same rules run three times:
`ConfigContract.check` in the build and at runtime (`/q/platform` reports `violations`), and `bdi_check` in
the Ansible role before a host is touched.

`required` and `secret` are what the pre-deploy check needs: every required, non-secret key present and not
blank in the rendered file; every secret key from the vault, never from inventory.

The contract also carries `"roles": ["admin", "operator", "reader"]`, the application roles the code names in
`@RolesAllowed`. When a security module is in use (its descriptor declares `quarkus.http.auth.roles-mapping.*`),
the platform must grant every role through at least one identity-provider group of the environment
(`quarkus.http.auth.roles-mapping."<AD group or realm role>"=role1,role2`): `missing()` reports `role:<name>`
otherwise, both in the Ansible pre-deploy check and on `/q/platform`, which also echoes `roles: {role: [groups]}`.
