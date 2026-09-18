package it.bancaditalia.quarkus.poc.remote.provider.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import it.bancaditalia.quarkus.test.oidc.RhbkLikeRealm;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(RhbkLikeRealm.class)
class CounterpartyResourceTest {

    static String reader() {
        return RhbkLikeRealm.token("alice", Set.of("app-readers"));
    }

    static String service() {
        return RhbkLikeRealm.token("svc-reports", Set.of("app-services"));
    }

    @Test
    void theFacadeAuthorizesTheCallerNotTheApplication() {
        given().when().get("/api/counterparties/IT0001").then().statusCode(401);
        given().auth().oauth2(reader()).when().get("/api/counterparties/IT0001")
                .then().statusCode(200).body("name", is("Banca di Prova"));
        given().auth().oauth2(reader()).when().get("/api/counterparties/XX9999").then().statusCode(404);
        given().auth().oauth2(reader()).contentType(ContentType.JSON).header("Idempotency-Key", "k1")
                .body(Map.of("code", "FR0003", "name", "Banque Test", "country", "FR"))
                .when().post("/api/counterparties").then().statusCode(403);
        given().when().get("/api/public/registry").then().statusCode(200).body("unit", org.hamcrest.Matchers.containsString("Anagrafe Controparti"));
    }

    @Test
    void registrationIsIdempotentOnTheCallersKey() {
        String key = UUID.randomUUID().toString();
        String code = "ES" + key.substring(0, 4).toUpperCase();
        Map<String, String> body = Map.of("code", code, "name", "Banco Prueba", "country", "ES");
        given().auth().oauth2(service()).contentType(ContentType.JSON).header("Idempotency-Key", key).body(body)
                .when().post("/api/counterparties").then().statusCode(201).body("code", is(code));
        // the retry: same key, same answer, nothing registered twice
        given().auth().oauth2(service()).contentType(ContentType.JSON).header("Idempotency-Key", key).body(body)
                .when().post("/api/counterparties").then().statusCode(200).body("code", is(code));
        // a different operation on the same code is a conflict
        given().auth().oauth2(service()).contentType(ContentType.JSON).header("Idempotency-Key", UUID.randomUUID().toString()).body(body)
                .when().post("/api/counterparties").then().statusCode(409);
    }
}
