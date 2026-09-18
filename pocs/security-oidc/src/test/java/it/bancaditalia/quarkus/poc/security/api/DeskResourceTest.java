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
import it.bancaditalia.quarkus.test.oidc.RhbkLikeRealm;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Against the RHBK look-alike of bdi-test-oidc: tokens minted with realm roles app-admins, app-operators,
 * app-readers, or none. The %test roles mapping grants admin/operator/reader to those realm roles. The
 * assertions are the LDAP POC's: same code, same outcomes, different credential.
 */
@QuarkusTest
@WithTestResource(RhbkLikeRealm.class)
class DeskResourceTest {

    static String alice() {
        return RhbkLikeRealm.token("alice", Set.of("app-admins"));
    }

    static String bob() {
        return RhbkLikeRealm.token("bob", Set.of("app-operators"));
    }

    static String carol() {
        return RhbkLikeRealm.token("carol", Set.of("app-readers"));
    }

    static String dave() {
        return RhbkLikeRealm.token("dave", Set.of());
    }

    @Test
    void anonymousIsChallengedExceptOnPublicPaths() {
        given().when().get("/api/requests")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalToIgnoringCase("bearer"));
        given().when().get("/api/public/status")
                .then().statusCode(200)
                .body("status", is("open"))
                .body("desk", containsString("Autorizzazioni"));
    }

    @Test
    void forgedAndGarbledTokensAreRefused() {
        given().auth().oauth2("not.a.token")
                .when().get("/api/requests").then().statusCode(401);
        String forged = alice().replaceFirst("\\.[^.]+$", ".AAAA"); // valid header and claims, wrong signature
        given().auth().oauth2(forged)
                .when().get("/api/requests").then().statusCode(401);
    }

    @Test
    void identityCarriesTheRealmRolesAndTheMappedRoles() {
        given().auth().oauth2(alice())
                .when().get("/api/me")
                .then().statusCode(200)
                .body("user", is("alice"))
                .body("roles", hasItems("app-admins", "admin", "operator", "reader"))
                .body("admin", is(true));
        given().auth().oauth2(carol())
                .when().get("/api/me")
                .then().statusCode(200)
                .body("roles", hasItems("app-readers", "reader"))
                .body("roles", not(hasItems("admin", "operator")))
                .body("admin", is(false));
        // a valid token with no realm role of this application: authenticated, but no role lets it in
        given().auth().oauth2(dave())
                .when().get("/api/me")
                .then().statusCode(200)
                .body("roles.size()", is(0));
        given().auth().oauth2(dave())
                .when().get("/api/requests")
                .then().statusCode(403);
    }

    @Test
    void rolesDecideWhoFilesReadsAndDecides() {
        given().auth().oauth2(carol())
                .when().get("/api/requests").then().statusCode(200);
        given().auth().oauth2(carol()).contentType(ContentType.JSON)
                .body(Map.of("applicant", "Banca di Prova", "subject", "new branch in Torino"))
                .when().post("/api/requests").then().statusCode(403);
        int id = given().auth().oauth2(bob()).contentType(ContentType.JSON)
                .body(Map.of("applicant", "Banca di Prova", "subject", "new branch in Torino"))
                .when().post("/api/requests")
                .then().statusCode(201)
                .body("status", is("PENDING"))
                .body("filedBy", is("bob"))
                .extract().path("id");
        given().auth().oauth2(bob()).contentType(ContentType.JSON)
                .body(Map.of("decision", "APPROVED"))
                .when().post("/api/requests/{id}/decision", id).then().statusCode(403);
        given().auth().oauth2(alice()).contentType(ContentType.JSON)
                .body(Map.of("decision", "APPROVED", "note", "ok"))
                .when().post("/api/requests/{id}/decision", id)
                .then().statusCode(200)
                .body("status", is("APPROVED"))
                .body("decidedBy", is("alice"));
        given().auth().oauth2(carol())
                .when().get("/api/requests/desk")
                .then().statusCode(200)
                .body("maxPending", is(3));
    }
}
