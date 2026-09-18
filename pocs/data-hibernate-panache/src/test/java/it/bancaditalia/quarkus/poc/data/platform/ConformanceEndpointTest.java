package it.bancaditalia.quarkus.poc.data.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConformanceEndpointTest {

    @ConfigProperty(name = "quarkus.management.test-port")
    int managementPort;

    @Test
    void reportsTheDerivedContractAndTheStandardizedDefaults() {
        given().port(managementPort)
                .when().get("/q/platform")
                .then().statusCode(200)
                .body("application.name", is("poc-data-hibernate-panache"))
                .body("missing", empty())
                .body("config.find { it.key == 'registry.supervisor' }.source", containsString("application.yaml"))
                .body("config.find { it.key == 'quarkus.flyway.migrate-at-start' }.source", is("BdiDefaults[bdi-config-flyway]"))
                .body("config.key", hasItems("registry.abi-pattern", "registry.max-page-size",
                        "quarkus.datasource.jdbc.url", "quarkus.datasource.jdbc.max-size"));
        given().port(managementPort)
                .when().get("/q/health/ready")
                .then().statusCode(200)
                .body("checks.name", hasItem(containsString("Database")));
    }
}
