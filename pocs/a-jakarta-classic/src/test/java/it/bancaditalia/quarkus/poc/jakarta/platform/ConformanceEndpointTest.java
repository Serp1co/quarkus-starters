package it.bancaditalia.quarkus.poc.jakarta.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConformanceEndpointTest {

    @ConfigProperty(name = "quarkus.management.test-port")
    int managementPort;

    @Test
    void reportsVersionProfileAndConfigSources() {
        given().port(managementPort)
                .when().get("/q/platform")
                .then().statusCode(200)
                .body("application.name", is("poc-a-jakarta-classic"))
                .body("application.version", is("1.0.0-SNAPSHOT"))
                .body("application.profiles", hasItem("test"))
                .body("missing", empty())
                .body("config.find { it.key == 'ledger.transfer.max-amount' }.value", is("1000.00"))
                .body("config.find { it.key == 'ledger.transfer.max-amount' }.source", containsString("application.properties"))
                .body("config.find { it.key == 'ledger.currency' }.value", is("EUR"))
                .body("config.find { it.key == 'quarkus.datasource.password' }.secret", is(true))
                .body("config.find { it.key == 'quarkus.datasource.password' }.value", anyOf(nullValue(), is("******")))
                .body("config.key", not(hasItem(containsString("*"))));
    }

    @Test
    void readinessIsOnTheManagementPort() {
        given().port(managementPort)
                .when().get("/q/health/ready")
                .then().statusCode(200)
                .body("status", is("UP"))
                .body("checks.name", hasItem(containsString("Database")));
    }
}
