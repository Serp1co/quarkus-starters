# POC messaging: Kafka

Design note §4.4. `quarkus-messaging-kafka` gives the same `@Incoming`/`@Outgoing` channel abstraction as
AMQP and none of the queue semantics: partitions, ordering per key, consumer groups, commit strategies,
no redelivery. This POC shows exactly those differences on a real Kafka 4.1 (sandbox) and under Dev
Services (CI). Status: all tests green. Kafka Streams and the Apicurio schema registry are called out
below, not built.

| Method | Path | What it does |
|---|---|---|
| POST | `/api/payments` | produces a keyed record (key = account); answers when the broker acknowledged it (`acks=all`) |
| GET | `/api/balances/{account}` | the consumer's projection: running balance, events applied, ordering evidence |
| GET | `/api/dead-letters` | what the dead-letter consumer saw |

## Semantics, one by one

| Queue habit (AMQP, JMS) | Kafka | Where |
|---|---|---|
| a message is consumed once, by one consumer | a record is read by every consumer **group**; the group's offset is what "consumed" means | `group.id` platform key |
| ordering per queue | ordering per **partition**; the key decides the partition, so one account's events stay in order, across accounts nothing is promised | `PaymentProducer` (key = account), `twentyEventsForOneAccountStayInOrder` |
| ack per message | commit per offset; `throttled` commits in order and never past an unacked record (standardized by `bdi-config-messaging-kafka`) | |
| nack = redelivery | nothing redelivers: a failed record goes to the **dead-letter topic** and the partition keeps flowing (`failure-strategy: dead-letter-queue`) | `aPoisonRecordGoesToTheDeadLetterTopicAndBlocksNothing` |
| at-least-once after a crash | the same: a rebalance replays records processed but not yet committed, hence the idempotent consumer (`processed_event` by event id) | `BalanceConsumer` |
| broker-side queue depth | retention: a new group reads from `earliest` (standardized) or `latest` | |

## What the developer wrote

`domain` (event record, balance projection, processed-event row), `messaging` (producer, consumer, dead-letter
consumer), `api`, `config`. `application.yaml`: `%dev`/`%test` topics, group ids and the Dev Services
topic list. Starters: `bdi-rest-jackson`, `bdi-jpa-postgresql`, `bdi-messaging-kafka`, `bdi-observability`,
`bdi-test`.

## What the platform provides

`kafka.bootstrap.servers`, `kafka.security.protocol`, `kafka.sasl.mechanism` (SASL/OAUTHBEARER with RHBK and
TLS with the security cookbook); the topics, the consumer-group names and the dead-letter topic as keys;
topics and ACLs provisioned as platform resources (AMQ Streams operator on OpenShift, or the Kafka admin
on VMs). Note for ops: a consumer subscribed to a topic that does not exist yet learns about it at the next
metadata refresh (five minutes by default); the platform creates topics before the application, and the
inner loop sets `metadata.max.age.ms` low for the dead-letter consumer.

## Run it

```bash
./mvnw quarkus:dev -pl pocs/messaging-kafka    # Dev Services start Kafka and PostgreSQL
./mvnw verify -DskipITs=false                   # or, without a container runtime:
export KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092
```

## Not built, on purpose

- **Kafka Streams** (`quarkus-kafka-streams`, supported): only for stateful processing (joins, windows,
  aggregations). It is operationally different: RocksDB `state.dir` persistent and node-local (an
  `instance` fragment key, like the XA object store), changelog and standby topics, rebalances. Build it
  when a real flow needs it, with its own starter.
- **Schema registry**: Avro with Apicurio Registry (`quarkus-apicurio-registry-avro`, supported) needs a
  registry instance; the JSON records here are the placeholder. Registry URL and credentials are platform
  keys when it comes.
- **Kafka transactions / exactly-once** (`quarkus-kafka-client`): for the rare flow that needs them.
