package it.bancaditalia.apps.sample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SampleTest {

    @ConfigProperty(name = "quarkus.management.test-port")
    int managementPort;

    @Test
    void theApplicationVersionIsItsOwnAndThePlatformIsThePlatforms() {
        given().when().get("/api/hello").then().statusCode(200).body("greeting", is("Buongiorno"));
        given().port(managementPort).when().get("/q/platform")
                .then().statusCode(200)
                .body("application.version", is("3.0.0"))
                .body("platform.version", is(not("3.0.0")))
                .body("platform.contractRevision", not(empty()))
                .body("missing", empty())
                .body("config.key", hasItems("sample.greeting", "quarkus.http.port", "quarkus.management.port"));
    }
}
