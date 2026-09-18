package it.bancaditalia.quarkus.poc.security.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import it.bancaditalia.quarkus.test.ldap.AdLikeDirectory;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(AdLikeDirectory.class)
class ConformanceEndpointTest {

    @ConfigProperty(name = "quarkus.management.test-port")
    int managementPort;

    @Test
    void reportsTheDerivedContractRolesAndTheirMapping() {
        given().port(managementPort)
                .when().get("/q/platform")
                .then().statusCode(200)
                .body("application.name", is("poc-security-ldap"))
                .body("missing", empty())
                .body("config.key", hasItems("desk.unit", "quarkus.security.ldap.dir-context.url",
                        "quarkus.security.ldap.dir-context.password", "quarkus.security.ldap.identity-mapping.search-base-dn",
                        "quarkus.security.ldap.identity-mapping.attribute-mappings.groups.filter-base-dn"))
                .body("config.find { it.key == 'quarkus.security.ldap.dir-context.password' }.value", is("******"))
                .body("config.find { it.key == 'quarkus.security.ldap.cache.max-age' }.source", is("BdiDefaults[bdi-config-security-ldap]"))
                // the roles the code names, and the groups the platform mapped to each
                .body("roles.keySet()", contains("admin", "operator", "reader"))
                .body("roles.admin", contains("APP-ADMINS"))
                .body("roles.reader", hasItems("APP-ADMINS", "APP-OPERATORS", "APP-READERS"));
        given().port(managementPort)
                .when().get("/q/health/ready")
                .then().statusCode(200);
    }
}
