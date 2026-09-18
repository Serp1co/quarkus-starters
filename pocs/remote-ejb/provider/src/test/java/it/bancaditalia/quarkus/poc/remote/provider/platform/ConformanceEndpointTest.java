package it.bancaditalia.quarkus.poc.remote.provider.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import it.bancaditalia.quarkus.test.oidc.RhbkLikeRealm;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(RhbkLikeRealm.class)
class ConformanceEndpointTest {

    @ConfigProperty(name = "quarkus.management.test-port")
    int managementPort;

    @Test
    void reportsTheDerivedContract() {
        given().port(managementPort)
                .when().get("/q/platform")
                .then().statusCode(200)
                .body("application.name", is("poc-remote-ejb-provider"))
                .body("missing", empty())
                .body("violations", empty())
                .body("config.key", hasItems("registry.unit", "quarkus.oidc.auth-server-url", "quarkus.grpc.server.use-separate-server"))
                .body("config.find { it.key == 'quarkus.grpc.server.use-separate-server' }.phase", is("build-time"))
                .body("roles.keySet()", contains("registry-reader", "registry-writer"));
    }
}
