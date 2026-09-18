package it.bancaditalia.quarkus.poc.amqp.messaging;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import it.bancaditalia.quarkus.poc.amqp.jms.JmsNotificationListener;
import it.bancaditalia.quarkus.poc.amqp.jms.JmsNotifier;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The JMS API on the same broker: transacted session, selector, redelivery after rollback. */
@QuarkusTest
class JmsVariationTest {

    @Inject
    JmsNotifier notifier;

    @Inject
    JmsNotificationListener listener;

    @Test
    void selectorTransactedSessionAndRedelivery() {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        notifier.notify("low-" + tag, "low priority, filtered out by the selector", 2);
        notifier.notify("high-" + tag, "high priority", 8);
        OrderFlowTest.await(() -> listener.received().stream().anyMatch(r -> r.contains("high-" + tag)), 20);
        Assertions.assertTrue(listener.received().stream().noneMatch(r -> r.contains("low-" + tag)));

        int redeliveriesBefore = listener.redeliveries();
        given().queryParam("reference", "poison-" + tag).queryParam("text", "POISON first time").queryParam("priority", 9)
                .when().post("/api/notifications").then().statusCode(200).body("sent", is(true));
        OrderFlowTest.await(() -> listener.received().stream().anyMatch(r -> r.contains("poison-" + tag) && r.contains("redelivered")), 20);
        Assertions.assertTrue(listener.redeliveries() > redeliveriesBefore);
    }
}
