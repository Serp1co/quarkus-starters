package it.bancaditalia.quarkus.poc.ejb.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConformanceEndpointTest {

    @ConfigProperty(name = "quarkus.management.test-port")
    int managementPort;

    @Test
    void reportsTheContractAndTheStandardizedDefaults() {
        given().port(managementPort)
                .when().get("/q/platform")
                .then().statusCode(200)
                .body("application.name", is("poc-b-ejb-heavy"))
                .body("missing", empty())
                // build-time keys are fixed in the artifact: the echo reports the fixed value, not the module that defaulted it
                .body("config.find { it.key == 'quarkus.quartz.clustered' }.value", is("true"))
                .body("config.find { it.key == 'quarkus.quartz.clustered' }.source", is("BuildTime RunTime Fixed"))
                .body("config.find { it.key == 'quarkus.flyway.migrate-at-start' }.source", is("BdiDefaults[bdi-config-flyway]"))
                .body("config.find { it.key == 'settlement.interval' }.source", containsString("application.yaml"));
        given().port(managementPort)
                .when().get("/q/health/ready")
                .then().statusCode(200)
                .body("checks.name", hasItem(containsString("Database")));
    }
}
