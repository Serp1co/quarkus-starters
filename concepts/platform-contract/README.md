# Concept: platform contract

The developer/platform contract of design notes §2.1 and §2.2, as code that every POC reuses.
Plain Java on top of SmallRye Config, no Quarkus dependency, so it runs in unit tests and in the
application alike. Candidate content for a `bdi-quarkus-*` internal extension (§2.3).

| Class | What it does |
|---|---|
| `ConfigContract` | The list of keys an application may or must receive. Built from the application's `@ConfigMapping` interfaces (owner `application`) plus the Quarkus keys of the extensions it uses (owner `platform`). Exports JSON (`toJson()`) for AAP and a Markdown table (`toMarkdownTable()`) for READMEs. Resolves itself against a live `Config` (`echo(config)`, `missing(config)`): which source served each key, with secrets masked. |
| `@Doc`, `@Secret` | Optional annotations on `@ConfigMapping` methods: a description for the ops audience, and the vault marker (never rendered from inventory, never echoed). |
| `ArtifactConfigLint` | Fails a build whose `application.properties` carries `%prod.` or any environment profile key. Only `%dev` and `%test` (the inner loop) are allowed in the artifact. |

## How a POC uses it

1. Declare what the application needs in a `@ConfigMapping` interface (defaults where a default is
   honest, no default where the environment must decide).
2. Define the contract once, `ConfigContract.builder().mapping(MyConfig.class).platform(...).build()`,
   listing the Quarkus keys of the extensions in use as platform keys.
3. Keep the export in sync with a test that compares `toJson()` with the versioned
   `src/main/resources/META-INF/config-contract.json` (see POC A's `ConfigContractTest`). The file ships
   inside the artifact, so AAP validates the rendered properties against exactly the artifact it deploys.
4. Expose `echo(config)` on the management port (POC A's `ConformanceEndpoint`, `GET /q/platform`) so the
   post-deploy conformance check of §3 reads version, active profile and config sources from the
   running instance.
5. Add the one-line lint test (`ArtifactConfigLint.assertNoBannedProfileKeys(...)`).

## Mapping rules covered

Kebab-case names, `@WithName`, `@WithParentName`, `@WithDefault`, nested groups, `Optional<T>`,
collections (`list<T>`), maps (`prefix.name.*`, one wildcard key per family, never required), groups
inside maps and lists. Bean Validation constraints on mapping methods are not exported yet; SmallRye
still enforces them at start-up.

## Contract JSON

```json
{
  "keys": [
    {"name": "ledger.currency", "type": "String", "required": false, "default": "EUR", "secret": false, "owner": "application", "doc": "..."},
    {"name": "ledger.transfer.max-amount", "type": "BigDecimal", "required": true, "default": null, "secret": false, "owner": "application", "doc": "..."},
    {"name": "quarkus.datasource.password", "type": "String", "required": true, "default": null, "secret": true, "owner": "platform", "doc": "..."}
  ]
}
```

`required` and `secret` are what an AAP pre-deploy check needs: every required, non-secret key must
be present in the rendered file; every secret key must come from the vault task, not from inventory.
