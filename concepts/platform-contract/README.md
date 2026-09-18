# Concept: platform contract

The developer/platform contract of design notes §2.1 and §2.2, **derived, never authored**. Nobody writes a
contract per application: the platform's parent POM runs `ContractExporter` on every application after
compilation, and the result (`META-INF/config-contract.json`, inside the artifact) is what the deploy role
validates the rendered files against and what the conformance endpoint resolves at runtime.

| Piece | What it does |
|---|---|
| `ContractExporter` | Scans the compiled classes for `@ConfigMapping` interfaces (keys, defaults, `@Doc`, `@Secret`) and messaging channels (`@Incoming`, `@Outgoing`, `@Channel`), merges the `PlatformDescriptor` of every `bdi-config-*` module on the classpath, writes the JSON. Bound in the root POM's `bdi-application` profile (exec-maven-plugin, phase `process-classes`), which activates by itself on any module that ships `src/main/resources/application.yaml`: applications get it, libraries never do, nobody declares it. `-Dbdi.contract.skip=true` is the escape hatch. |
| `PlatformDescriptor` | What a config module declares in its `META-INF/bdi-contract-platform.json`: the Quarkus keys of the extensions it configures, and key templates for the channels an application declares (`{channel}`, `{application}` substituted). Written once per module by the platform team, never per application. |
| `ConfigContract` | The keys, JSON in and out, and the runtime echo (`echo(config)`: value, source and ordinal of every key, secrets masked; `missing(config)`). |
| `@Doc`, `@Secret` | Optional annotations on `@ConfigMapping` methods: the description ops read, and the vault marker. |
| `ArtifactConfigLint` | Fails a build whose `application.yaml` carries a `"%prod":` section or any environment profile. |

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

`required` and `secret` are what the pre-deploy check needs: every required, non-secret key present in the
rendered file; every secret key from the vault, never from inventory.
