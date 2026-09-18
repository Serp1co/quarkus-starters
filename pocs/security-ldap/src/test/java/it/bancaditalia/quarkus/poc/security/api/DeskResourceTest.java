package it.bancaditalia.quarkus.poc.security.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import it.bancaditalia.quarkus.test.ldap.AdLikeDirectory;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Against the AD look-alike of bdi-test-ldap: alice (APP-ADMINS), bob (APP-OPERATORS), carol (APP-READERS),
 * dave (no group). The %test roles mapping grants admin/operator/reader to those groups.
 */
@QuarkusTest
@WithTestResource(AdLikeDirectory.class)
class DeskResourceTest {

    @Test
    void anonymousIsChallengedExceptOnPublicPaths() {
        given().when().get("/api/requests")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalToIgnoringCase("basic"));
        given().when().get("/api/public/status")
                .then().statusCode(200)
                .body("status", is("open"))
                .body("desk", containsString("Autorizzazioni"));
    }

    @Test
    void wrongPasswordAndUnknownUserAreRefused() {
        given().auth().preemptive().basic("alice", "wrong")
                .when().get("/api/requests").then().statusCode(401);
        given().auth().preemptive().basic("nobody", "nobody-pw")
                .when().get("/api/requests").then().statusCode(401);
    }

    @Test
    void identityCarriesTheAdGroupsAndTheMappedRoles() {
        given().auth().preemptive().basic("alice", "alice-pw")
                .when().get("/api/me")
                .then().statusCode(200)
                .body("user", is("alice"))
                .body("roles", hasItems("APP-ADMINS", "admin", "operator", "reader"))
                .body("admin", is(true));
        given().auth().preemptive().basic("carol", "carol-pw")
                .when().get("/api/me")
                .then().statusCode(200)
                .body("roles", hasItems("APP-READERS", "reader"))
                .body("roles", not(hasItems("admin", "operator")))
                .body("admin", is(false));
        // a valid AD account in no application group: authenticated, but no role lets it in
        given().auth().preemptive().basic("dave", "dave-pw")
                .when().get("/api/me")
                .then().statusCode(200)
                .body("roles.size()", is(0));
        given().auth().preemptive().basic("dave", "dave-pw")
                .when().get("/api/requests")
                .then().statusCode(403);
    }

    @Test
    void rolesDecideWhoFilesReadsAndDecides() {
        // a reader may read but not file
        given().auth().preemptive().basic("carol", "carol-pw")
                .when().get("/api/requests").then().statusCode(200);
        given().auth().preemptive().basic("carol", "carol-pw").contentType(ContentType.JSON)
                .body(Map.of("applicant", "Banca di Prova", "subject", "new branch in Torino"))
                .when().post("/api/requests").then().statusCode(403);
        // an operator files
        int id = given().auth().preemptive().basic("bob", "bob-pw").contentType(ContentType.JSON)
                .body(Map.of("applicant", "Banca di Prova", "subject", "new branch in Torino"))
                .when().post("/api/requests")
                .then().statusCode(201)
                .body("status", is("PENDING"))
                .body("filedBy", is("bob"))
                .extract().path("id");
        // but may not decide
        given().auth().preemptive().basic("bob", "bob-pw").contentType(ContentType.JSON)
                .body(Map.of("decision", "APPROVED"))
                .when().post("/api/requests/{id}/decision", id).then().statusCode(403);
        // an admin decides
        given().auth().preemptive().basic("alice", "alice-pw").contentType(ContentType.JSON)
                .body(Map.of("decision", "APPROVED", "note", "ok"))
                .when().post("/api/requests/{id}/decision", id)
                .then().statusCode(200)
                .body("status", is("APPROVED"))
                .body("decidedBy", is("alice"));
        given().auth().preemptive().basic("carol", "carol-pw")
                .when().get("/api/requests/{id}", id)
                .then().statusCode(200)
                .body("status", is("APPROVED"));
        given().auth().preemptive().basic("carol", "carol-pw")
                .when().get("/api/requests/desk")
                .then().statusCode(200)
                .body("maxPending", is(3));
    }
}
