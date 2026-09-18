package it.bancaditalia.quarkus.poc.kafka.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

/** The contract is derived at build time: the application's keys, its channels, the modules' platform keys. */
@QuarkusTest
class ConformanceEndpointTest {

    @ConfigProperty(name = "quarkus.management.test-port")
    int managementPort;

    @Test
    void theDerivedContractCoversTheApplicationItsChannelsAndThePlatform() {
        given().port(managementPort)
                .when().get("/q/platform")
                .then().statusCode(200)
                .body("application.name", is("poc-messaging-kafka"))
                .body("missing", empty())
                .body("config.key", hasItems("kafka.bootstrap.servers", "mp.messaging.incoming.payments-in.group.id", "mp.messaging.incoming.payments-in.dead-letter-queue.topic", "mp.messaging.outgoing.payments-out.topic"));
    }
}
