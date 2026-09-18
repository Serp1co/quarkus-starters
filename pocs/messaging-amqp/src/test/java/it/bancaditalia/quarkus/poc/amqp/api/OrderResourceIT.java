package it.bancaditalia.quarkus.poc.amqp.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import it.bancaditalia.quarkus.poc.amqp.platform.PlatformConfigResource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@WithTestResource(value = PlatformConfigResource.class, scope = TestResourceScope.RESTRICTED_TO_CLASS)
class OrderResourceIT {

    @Test
    void endToEndOnThePackagedArtifact() {
        String reference = "IT-" + UUID.randomUUID().toString().substring(0, 8);
        given().contentType(ContentType.JSON).body(Map.of("reference", reference, "amount", new BigDecimal("3.00")))
                .when().post("/api/orders").then().statusCode(202);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (!"PROCESSED".equals(given().get("/api/orders/{r}", reference).then().extract().path("status"))) {
            Assertions.assertTrue(Instant.now().isBefore(deadline), "processed within 20s");
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        given().get("/api/orders/{r}", reference).then().body("deliveries", is(1));
    }
}
