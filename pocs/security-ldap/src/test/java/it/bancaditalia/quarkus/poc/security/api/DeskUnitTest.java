package it.bancaditalia.quarkus.poc.security.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import it.bancaditalia.quarkus.test.ldap.AdLikeDirectory;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The resource logic with a faked identity: {@code @TestSecurity} (bdi-test) sets the user and roles, so no
 * request of these tests touches the directory. The realm still needs its platform keys to start (they are
 * required), hence the same test resource as everywhere else; the app boots once for both test classes.
 * The realm itself is tested once, in DeskResourceTest.
 */
@QuarkusTest
@WithTestResource(AdLikeDirectory.class)
class DeskUnitTest {

    @Test
    @TestSecurity(user = "tester", roles = "operator")
    void anOperatorFilesUpToTheLimit() {
        for (int i = 0; i < 3; i++) {
            given().contentType(ContentType.JSON)
                    .body(Map.of("applicant", "A" + i, "subject", "s"))
                    .when().post("/api/requests").then().statusCode(201);
        }
        given().contentType(ContentType.JSON)
                .body(Map.of("applicant", "one too many", "subject", "s"))
                .when().post("/api/requests")
                .then().statusCode(422); // desk.max-pending = 3 in %test
    }

    @Test
    @TestSecurity(user = "tester", roles = "reader")
    void aReaderCannotFile() {
        given().contentType(ContentType.JSON)
                .body(Map.of("applicant", "A", "subject", "s"))
                .when().post("/api/requests").then().statusCode(403);
        given().when().get("/api/me").then().statusCode(200).body("user", is("tester"));
    }
}
