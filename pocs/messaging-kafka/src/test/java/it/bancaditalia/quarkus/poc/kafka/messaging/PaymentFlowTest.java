package it.bancaditalia.quarkus.poc.kafka.messaging;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Produce keyed records, consume into balances, poison to the dead-letter topic, ordering per key. */
@QuarkusTest
class PaymentFlowTest {

    @Inject
    BalanceConsumer consumer;

    @Test
    void eventsAreAppliedOncePerAccountInOrder() {
        String a = "ACC-A-" + UUID.randomUUID().toString().substring(0, 6);
        String b = "ACC-B-" + UUID.randomUUID().toString().substring(0, 6);
        pay(a, "10.00");
        pay(b, "7.00");
        pay(a, "5.00");
        await(() -> events(a) == 2, 30);
        await(() -> events(b) == 1, 30);
        given().get("/api/balances/{a}", a).then().body("balance", org.hamcrest.Matchers.is(15.0f));
        given().get("/api/balances/{a}", b).then().body("balance", org.hamcrest.Matchers.is(7.0f));
    }

    @Test
    void twentyEventsForOneAccountStayInOrder() {
        String account = "ACC-ORD-" + UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < 20; i++) {
            pay(account, "1.00");
        }
        await(() -> events(account) == 20, 40);
        given().get("/api/balances/{a}", account).then().body("outOfOrder", org.hamcrest.Matchers.is(0)); // same key, same partition, same order
    }

    @Test
    void aPoisonRecordGoesToTheDeadLetterTopicAndBlocksNothing() {
        String poison = "POISON-" + UUID.randomUUID().toString().substring(0, 6);
        String next = "ACC-N-" + UUID.randomUUID().toString().substring(0, 6);
        String eventId = pay(poison, "1.00");
        pay(next, "2.00");
        await(() -> consumer.deadLetters().stream().anyMatch(d -> d.startsWith(eventId)), 30);
        await(() -> events(next) == 1, 30);
        given().get("/api/balances/{a}", poison).then().statusCode(404);
    }

    /** Through the API: a JPA read in the test thread would keep its first-level cache for the whole test. */
    static int events(String account) {
        var response = given().get("/api/balances/{a}", account);
        return response.statusCode() == 200 ? response.then().extract().path("events") : -1;
    }

    static String pay(String account, String amount) {
        return given().contentType(ContentType.JSON).body(Map.of("account", account, "amount", new BigDecimal(amount)))
                .when().post("/api/payments").then().statusCode(200).extract().path("eventId");
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
