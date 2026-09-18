package it.bancaditalia.quarkus.poc.security.platform;

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
    void reportsTheDerivedContractRolesAndTheirMapping() {
        given().port(managementPort)
                .when().get("/q/platform")
                .then().statusCode(200)
                .body("application.name", is("poc-security-oidc"))
                .body("missing", empty())
                .body("config.key", hasItems("desk.unit", "quarkus.oidc.auth-server-url", "quarkus.oidc.client-id",
                        "quarkus.oidc.credentials.secret", "quarkus.oidc.application-type"))
                .body("config.find { it.key == 'quarkus.oidc.credentials.secret' }.value", is("******"))
                .body("config.find { it.key == 'quarkus.oidc.application-type' }.source", is("BdiDefaults[bdi-config-security-oidc]"))
                .body("roles.keySet()", contains("admin", "operator", "reader"))
                .body("roles.admin", contains("app-admins"))
                .body("roles.reader", hasItems("app-admins", "app-operators", "app-readers"));
        given().port(managementPort)
                .when().get("/q/health/ready")
                .then().statusCode(200);
    }
}
