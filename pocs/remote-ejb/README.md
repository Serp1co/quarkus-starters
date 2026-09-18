# POC remote EJB: the replacement matrix

Design note §4.2. There is no EJB container in Quarkus, so a remote EJB is not ported, it is replaced, and the
replacement depends on what the call was for. Two applications show the matrix on one case: the *reporting*
application (the caller) used to look counterparties up in the *registry* (the provider) through
`@EJB CounterpartyService registry;` over `remote+http`, with the caller's identity and transaction propagated
by the containers. Status: 5 + 4 tests and 2 + 0 integration tests green, in the sandbox and in CI; the two
applications talk through a stubbed provider in the caller's tests and through a real gRPC client on the
provider's side (no second process needed).

| Directory | Was | Is |
|---|---|---|
| [`api`](api/) | the remote-interface jar (`CounterpartyService`, DTOs) | the same idea: two REST client interfaces and their DTOs, the `.proto` and its generated stubs. Versioned and published like the EJB client jar, optional on the starters so that each side adds only what it uses |
| [`provider`](provider/) | `@Stateless @Remote(CounterpartyService.class)` | the same bean behind a JAX-RS façade (the default) and a gRPC service (the typed contract), on one unified port with bearer authentication |
| [`caller`](caller/) | `@EJB` injection, identity and transaction propagated | a REST client with the user's token propagated, a second one with the application's own identity, a gRPC stub with the token propagated by the platform, an idempotency key where the transaction used to be |

## The matrix

| The remote call was | Replace it with | In the POC |
|---|---|---|
| synchronous request/response, on behalf of the user | MicroProfile REST Client + `@AccessToken`: the user's bearer token travels, the provider authorizes the user | `CounterpartyUserClient`, `ReportService.file` |
| synchronous, as the application itself (batch, timer, retry) | REST Client + `@OidcClientFilter`: a token from the application's own RHBK client (client credentials) | `CounterpartyServiceClient`, `ReportService.register` |
| a typed contract, streaming, or many small calls | gRPC: the `.proto` is the interface, generated on both sides, served by the unified server so the same bearer applies; the platform propagates the token on every outbound call | `counterparty.proto`, `CounterpartyGrpcService`, `ReportService.verify`, `BearerPropagationInterceptor` |
| fire-and-forget, batch | messaging: the [AMQP](../messaging-amqp/) and [Kafka](../messaging-kafka/) POCs | not repeated here |
| a call inside the caller's transaction | there is none across the wire: store the local fact first, make the remote step idempotent (`Idempotency-Key` = the caller's own id), retry, and let the status say how far it got | `Report.Status`, `POST /api/reports/{id}/retry`, the provider's key map |
| coexistence during the migration | the provider's JAX-RS façade is the class an EAP 8 host deploys over the old EJBs; Quarkus callers call REST from day one, EAP callers of Quarkus need nothing | `CounterpartyResource` |
| the EJB client jars from a plain JVM | works in JVM mode, unsupported: not built (design note §4.2) | |

## What replaces what, in code

| On EAP | Here | Where |
|---|---|---|
| `@EJB CounterpartyService registry` | `@Inject @RestClient CounterpartyUserClient asUser` and `@GrpcClient("counterparty") CounterpartyRegistry typed` | `ReportService` |
| `remote+http` outbound connection in `standalone.xml` | `quarkus.rest-client.counterparty.url`, `quarkus.grpc.clients.counterparty.host/port`: platform keys derived per injected client into the contract | `bdi-config-rest-client`, `bdi-config-grpc`, `ContractExporter` |
| caller identity propagated by the EJB client | the bearer token propagated: `@AccessToken` on REST, the platform's gRPC interceptor | `bdi-rest-client-oidc`, `bdi-config-grpc` |
| `@RunAs` on a batch EJB | the application's own client: `@OidcClientFilter`, `quarkus.oidc-client.*` platform keys, the secret from the vault | `bdi-config-oidc-client` |
| `@TransactionAttribute(REQUIRED)` spanning the remote call | store first, idempotent remote step, retry endpoint, status | `ReportService`, `CounterpartyRegistryBean.register` |
| `@RolesAllowed` on the remote bean | `@RolesAllowed` on the façade and on the gRPC service; realm roles mapped by the platform | provider `application.yaml` (`%test`), the contract's `roles` |
| the remote interface jar in the caller's classpath | `poc-remote-ejb-api` | `api/pom.xml` |

## As tested

- The caller's tests run a stubbed provider (REST resource and gRPC service in `src/test`) on the caller's own
  test port, under the mock realm of `bdi-test-oidc`. The stub records the identity every call carried:
  `find` arrives as `alice` (propagated), `register` as `service-account-poc-caller` (the application's own
  token, minted by the mock realm on the client-credentials grant) with the report id as `Idempotency-Key`,
  and the gRPC lookup as `alice` again. A retry replays the same key and gets 200, not a second registration.
- The provider's tests call the façade with tokens (401, 403, 200, 201, 200 on the replay, 409 on a duplicate
  code) and the gRPC service through a real client with the bearer in the metadata (`PERMISSION_DENIED` for an
  anonymous or under-privileged call, `NOT_FOUND`, `ALREADY_EXISTS`, `created=false` on the replay).
- Both contracts are derived: the caller's names `quarkus.rest-client.counterparty.*`,
  `quarkus.grpc.clients.counterparty.*` and `quarkus.oidc-client.*` next to its inbound realm keys; the
  provider's names `quarkus.grpc.server.use-separate-server` as a build-time key and the roles
  `registry-reader`, `registry-writer`.

## Run it

```bash
./mvnw verify -pl pocs/remote-ejb/api,pocs/remote-ejb/provider,pocs/remote-ejb/caller -DskipITs=false
```

Two processes end to end need a realm both trust: in dev mode (`quarkus:dev` in each directory) Keycloak Dev
Services starts one per application, so point both at the same realm through `quarkus.oidc.auth-server-url`
and give the caller its client (`quarkus.oidc-client.*`). On a stage of the ladder the platform renders all of
it; the caller's `%dev` values assume the provider on 8080 and the caller on 8090.

## Measured numbers

| Metric | Value |
|---|---|
| Start-up to ready, prod profile, provider (REST + gRPC on one port) | about 2 s in the sandbox |
| Test suite | provider 5 + 2 integration, caller 4 |
| Config lines owned by the developer (non-profile) | 0 in both |
| Lines to replace the `@EJB` injection in the caller | 3 injection points, 0 platform-specific code |

## Gaps and next steps (cookbook 7)

- **Sagas**: MicroProfile LRA (Narayana) for multi-step business transactions across services; its RHBQ
  support status to check before recommending it.
- **Token exchange** (RHBK) when the provider must see a token minted for itself rather than the caller's
  audience; `quarkus.oidc.token.audience` on the provider is where it bites.
- **mTLS between services** through the TLS registry, for the service identity where no realm is involved.
- **Client-side resilience**: `@Retry`/`@Timeout`/`@CircuitBreaker` (SmallRye Fault Tolerance) on the client
  interfaces, standardized by `bdi-config-rest-client`.
- **gRPC streaming** and deadlines, not exercised by unary calls.
