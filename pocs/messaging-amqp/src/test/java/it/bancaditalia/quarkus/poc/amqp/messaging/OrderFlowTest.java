package it.bancaditalia.quarkus.poc.amqp.messaging;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import it.bancaditalia.quarkus.poc.amqp.repository.OrderRepository;
import it.bancaditalia.quarkus.poc.amqp.service.OrderService;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Outbox to broker to consumer, with the failure modes a payments team asks about (design note 4.3).
 * State is read through the API: a JPA read in the test thread would keep its first-level cache for the whole test.
 */
@QuarkusTest
class OrderFlowTest {

    @Inject
    OrderService orders;

    @Inject
    OrderRepository repository;

    @Inject
    OrderConsumer consumer;

    @Inject
    OutboxRelay relay;

    @Test
    void anOrderIsPublishedThroughTheOutboxAndProcessedOnce() {
        String reference = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        given().contentType(ContentType.JSON).body(Map.of("reference", reference, "amount", new BigDecimal("10.00")))
                .when().post("/api/orders").then().statusCode(202).body("status", is("NEW"));

        await(() -> "PROCESSED".equals(status(reference)), 20);
        given().get("/api/orders/{r}", reference).then().body("deliveries", is(1));
    }

    @Test
    void aRedeliveredMessageIsSkippedByTheIdempotentConsumer() {
        String reference = "DUP-" + UUID.randomUUID().toString().substring(0, 8);
        orders.submit(reference, new BigDecimal("5.00"));
        await(() -> "PROCESSED".equals(status(reference)), 20);
        int duplicatesBefore = consumer.duplicates();

        // the same outbox row (same message id) leaves the outbox again, as after a lost ack or a relay retry
        Assertions.assertTrue(relay.resend(repository.lockPendingIdsFor(reference)));

        await(() -> consumer.duplicates() > duplicatesBefore, 20);
        given().get("/api/orders/{r}", reference).then().body("status", is("PROCESSED")).body("deliveries", is(1));
    }

    @Test
    void aPoisonMessageIsRedeliveredThenDeadLettered() {
        String reference = "POISON-" + UUID.randomUUID().toString().substring(0, 8);
        orders.submit(reference, new BigDecimal("1.00"));
        // nack -> modified(delivery-failed) -> Artemis redelivers up to max-delivery-attempts (10), then the DLQ
        await(() -> consumer.failures().stream().filter(f -> f.contains(reference)).count() >= 3, 30);
        Assertions.assertEquals("NEW", status(reference)); // never applied: every attempt rolled back
    }

    static String status(String reference) {
        return given().get("/api/orders/{r}", reference).then().statusCode(200).extract().path("status");
    }

    static void await(BooleanSupplier condition, int seconds) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(seconds));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                Assertions.fail("condition not met within " + seconds + "s");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
