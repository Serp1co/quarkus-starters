package it.bancaditalia.quarkus.poc.remote.caller.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.oidc.server.OidcWireMock;
import io.restassured.http.ContentType;
import it.bancaditalia.quarkus.poc.remote.caller.platform.CallerRealm;
import it.bancaditalia.quarkus.poc.remote.caller.stub.StubCounterpartyResource;
import it.bancaditalia.quarkus.test.oidc.RhbkLikeRealm;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The caller against the stubbed provider on its own test port, both under the mock realm: the user's token
 * reaches the provider on the lookup, the application's own token on the registration, the report id is the
 * idempotency key, and the gRPC lookup carries the user's token too.
 */
@QuarkusTest
@WithTestResource(CallerRealm.class)
class ReportResourceTest {

    @OidcWireMock
    WireMockServer realm;

    static String alice() {
        return RhbkLikeRealm.token("alice", Set.of("app-reporters"));
    }

    @BeforeEach
    void theRealmIssuesTheApplicationItsOwnToken() {
        // the client credentials grant of the application's client: a token for the service account
        realm.stubFor(post(urlPathEqualTo("/auth/realms/quarkus/token"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"" + RhbkLikeRealm.token("service-account-poc-caller", Set.of("app-services"))
                                + "\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
        StubCounterpartyResource.CALLS.clear();
    }

    @Test
    void aKnownCounterpartyIsLookedUpAsTheUser() {
        String id = given().auth().oauth2(alice()).contentType(ContentType.JSON)
                .body(Map.of("counterpartyCode", "IT0001", "amount", 100))
                .when().post("/api/reports")
                .then().statusCode(201)
                .body("status", is("COMPLETE"))
                .body("counterpartyName", is("Banca di Prova"))
                .body("filedBy", is("alice"))
                .extract().path("id");
        StubCounterpartyResource.Call find = StubCounterpartyResource.CALLS.get(0);
        assertEquals("find", find.operation());
        assertEquals("alice", find.principal(), "the user's token was propagated: the registry saw alice, not the application");
        assertTrue(find.roles().contains("registry-reader"));
        // the same lookup over gRPC, the token propagated by the platform interceptor
        given().auth().oauth2(alice()).when().get("/api/reports/{id}/verify", id)
                .then().statusCode(200).body("via", is("grpc")).body("name", is("Banca di Prova"));
        StubCounterpartyResource.Call grpc = StubCounterpartyResource.CALLS.get(1);
        assertEquals("grpc-find", grpc.operation());
        assertEquals("alice", grpc.principal());
    }

    @Test
    void anUnknownCounterpartyIsRegisteredByTheApplicationIdempotently() {
        String id = given().auth().oauth2(alice()).contentType(ContentType.JSON)
                .body(Map.of("counterpartyCode", "NL0009", "amount", 50, "counterpartyName", "Testbank", "country", "NL"))
                .when().post("/api/reports")
                .then().statusCode(201)
                .body("status", is("COMPLETE"))
                .body("counterpartyName", is("Testbank"))
                .extract().path("id");
        List<StubCounterpartyResource.Call> calls = StubCounterpartyResource.CALLS;
        assertEquals("find", calls.get(0).operation());
        assertEquals("register", calls.get(1).operation());
        assertEquals("service-account-poc-caller", calls.get(1).principal(), "the registration is the application's own act");
        assertEquals(id, calls.get(1).idempotencyKey(), "the report id is the idempotency key");
        // the retry of the remote step replays the same key: the registry answers 200, nothing is registered twice
        given().auth().oauth2(alice()).contentType(ContentType.JSON)
                .body(Map.of("counterpartyName", "Testbank", "country", "NL"))
                .when().post("/api/reports/{id}/retry", id)
                .then().statusCode(200).body("status", is("COMPLETE"));
    }

    @Test
    void withoutNameAndCountryTheReportStaysPendingUntilRetried() {
        String id = given().auth().oauth2(alice()).contentType(ContentType.JSON)
                .body(Map.of("counterpartyCode", "BE0007", "amount", 5))
                .when().post("/api/reports")
                .then().statusCode(201)
                .body("status", is("PENDING_REGISTRY"))
                .extract().path("id");
        given().auth().oauth2(alice()).contentType(ContentType.JSON)
                .body(Map.of("counterpartyName", "Banque Test", "country", "BE"))
                .when().post("/api/reports/{id}/retry", id)
                .then().statusCode(200).body("status", is("COMPLETE"));
        given().when().get("/api/reports").then().statusCode(401);
        given().auth().oauth2(alice()).when().get("/api/reports/desk").then().statusCode(200).body("unit", is("Segnalazioni (test)"));
    }
}
