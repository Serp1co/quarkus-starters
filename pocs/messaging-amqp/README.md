# POC messaging: queues and topics over AMQP 1.0

Design note §4.3. The question a payments team asks is not "which API" but "what happens to my MDB + JPA
in one XA transaction". The answer this POC demonstrates: **no XA across broker and database**, a
transactional outbox on the producer side, an idempotent consumer on the other, broker redelivery and a
dead-letter queue for poison messages. Two APIs against the same AMQ Broker (Artemis) and the same
platform keys: Reactive Messaging (the default, RHBQ-supported) and the JMS API (Qpid JMS, amqphub
extension, not in the RHBQ platform). Status: all tests green against a real Artemis 2.44 in the sandbox
and under Dev Services in CI; IBM MQ is the next variation.

| Method | Path | What it does |
|---|---|---|
| POST | `/api/orders` | writes the order and its event in **one local transaction** (outbox); answers 202 |
| GET | `/api/orders/{reference}` | status, processed time, how many deliveries the consumer applied |
| GET | `/api/consumer` | deliveries, duplicates skipped, failures (the ex-MDB's counters) |
| POST | `/api/outbox/{id}/resend` | POC hook: republish an outbox row to show the idempotent consumer |
| POST/GET | `/api/notifications` | the JMS variation: send with priority and property, read what the selector-filtered listener got |

## What replaces what

| On EAP | Here | Where |
|---|---|---|
| `@MessageDriven` + JPA in one XA transaction | `@Incoming` with manual ack after a local JPA transaction; nack on failure. No XA: the broker redelivers, the consumer is idempotent | `OrderConsumer` |
| JMS send inside the same XA transaction as the JPA write | the **outbox**: event row written with the business row, published afterwards by a relay that marks it sent only when the broker accepted it (at-least-once) | `OrderService`, `OutboxRelay` |
| MDB rollback returns the message to the queue | `failure-strategy: modified-failed` (standardized by `bdi-config-messaging-amqp`): the broker redelivers, then dead-letters after `max-delivery-attempts` (10 on Artemis by default) | `bdi-config-messaging-amqp` |
| duplicate delivery after a crash between commit and ack | `processed_message` table keyed by the AMQP message id (= outbox id): a redelivery changes nothing | `OrderConsumer.process` |
| activation-config `messageSelector`, transacted session | the JMS API: `createContext(SESSION_TRANSACTED)`, `createConsumer(queue, "JMSPriority >= 5")`, rollback and `JMSRedelivered` | `JmsNotifier`, `JmsNotificationListener` |
| queue names in `standalone.xml`, JNDI lookups | the developer names channels (`orders-out`, `orders-in`); addresses are platform keys with the channel name as default; the topology is `redhat.amq_broker`'s | `bdi-config-messaging-amqp` descriptor, derived per channel |

## The failure modes, as tested

| Case | Test | Observed |
|---|---|---|
| happy path | `anOrderIsPublishedThroughTheOutboxAndProcessedOnce` | relay publishes within 1 s, consumer applies once, `deliveries = 1` |
| redelivery / duplicate | `aRedeliveredMessageIsSkippedByTheIdempotentConsumer` | the same message id arrives again, `duplicates + 1`, the order stays applied once |
| poison message | `aPoisonMessageIsRedeliveredThenDeadLettered` | nacked, redelivered ten times within 100 ms by Artemis, then moved to `DLQ`; the order stays `NEW`, every attempt rolled back |
| JMS selector and rollback | `JmsVariationTest` | priority 2 stays in the queue, priority 8 is received; a rolled-back message comes back with `JMSRedelivered` |
| broker down | not automated | the relay keeps the rows unsent and retries every second; readiness reports the broker (`health-enabled`) |

## What the developer wrote

`domain` (order, outbox row, processed-message row, the wire event), `repository` (SKIP LOCKED for the
relay), `service` (submit = order + outbox), `messaging` (relay, consumer), `jms` (the variation), `api`,
`config`. `application.yaml`: `%dev`/`%test` channel addresses only. Starters: `bdi-rest-jackson`,
`bdi-jpa-postgresql`, `bdi-messaging-amqp`, `bdi-jms-amqp`, `bdi-scheduler-local` (the relay is
per instance: SKIP LOCKED keeps two relays apart), `bdi-observability`, `bdi-test`.

## What the platform provides

`amqp-host`, `amqp-port`, `amqp-username`, `amqp-password` (vault), TLS later; the addresses of the
channels; the broker topology (addresses, queues, DLQ, max-delivery-attempts, redelivery delay) through
`redhat.amq_broker`. `platform/config/application-uat.yaml` and `platform/secrets/secrets.yaml` are the
rendered examples; the same `secrets.yaml,instance.yaml,application-<env>.yaml` convention applies.

## Run it

```bash
./mvnw quarkus:dev -pl pocs/messaging-amqp     # Dev Services start Artemis (AMQP 1.0) and PostgreSQL
./mvnw verify -DskipITs=false                   # or, without a container runtime, against your own broker:
export AMQP_HOST=127.0.0.1 AMQP_PORT=5672 AMQP_USERNAME=poc AMQP_PASSWORD=poc
```

## Gaps and next steps

- **IBM MQ**: JMS through the IBM MQ client, resource adapter through `quarkus-ironjacamar` (Quarkiverse,
  unsupported); a `bdi-jms-ibmmq` starter with the same `JmsNotifier`/listener code. Needs an MQ instance.
- **XA where it is worth it**: `quarkus-pooled-jms` (Quarkiverse) gives XA-capable JMS pooling for the
  applications that cannot adopt the outbox; to be measured, not assumed.
- **Kafka** is the other POC: same channel abstraction, different semantics.
- `bdi-jms-amqp` pins `quarkus-qpid-jms` 2.12.0, the release built for Quarkus 3.33.0 (2.13.0 targets
  3.39 and fails at runtime on proton-j). Re-check on every stream bump; it is outside the RHBQ support.
