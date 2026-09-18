package it.bancaditalia.quarkus.poc.remote.caller.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(CallerRealm.class)
class ConformanceEndpointTest {

    @ConfigProperty(name = "quarkus.management.test-port")
    int managementPort;

    @Test
    void theContractNamesTheClientsTheApplicationInjects() {
        given().port(managementPort)
                .when().get("/q/platform")
                .then().statusCode(200)
                .body("application.name", is("poc-remote-ejb-caller"))
                .body("missing", empty())
                .body("violations", empty())
                .body("config.key", hasItems("reports.unit",
                        "quarkus.rest-client.counterparty.url", "quarkus.rest-client.counterparty.read-timeout",
                        "quarkus.grpc.clients.counterparty.host", "quarkus.grpc.clients.counterparty.port",
                        "quarkus.oidc-client.auth-server-url", "quarkus.oidc-client.credentials.secret",
                        "quarkus.oidc.auth-server-url"))
                .body("config.find { it.key == 'quarkus.rest-client.counterparty.url' }.source", org.hamcrest.Matchers.containsString("application.yaml"))
                .body("roles.keySet()", contains("reporter"));
    }
}
