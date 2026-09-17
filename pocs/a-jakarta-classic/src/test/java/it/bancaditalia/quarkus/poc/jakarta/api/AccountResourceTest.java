package it.bancaditalia.quarkus.poc.jakarta.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import it.bancaditalia.quarkus.poc.jakarta.Ibans;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AccountResourceTest {

    @Test
    void opensAndReadsAnAccount() {
        String iban = Ibans.next();
        given().contentType(ContentType.JSON)
                .body(Map.of("iban", iban, "holder", "Mario Rossi", "initialBalance", new BigDecimal("100.50")))
                .when().post("/api/accounts")
                .then().statusCode(201)
                .header("Location", endsWith("/api/accounts/" + iban))
                .body("iban", is(iban))
                .body("balance", is(100.5f))
                .body("currency", is("EUR"));

        given().when().get("/api/accounts/{iban}", iban)
                .then().statusCode(200)
                .body("holder", is("Mario Rossi"))
                .body("createdAt", notNullValue());
    }

    @Test
    void listsAccounts() {
        String iban = Ibans.next();
        open(iban, "Anna Bianchi", "10.00");
        given().when().get("/api/accounts")
                .then().statusCode(200)
                .body("iban", hasItem(iban));
    }

    @Test
    void rejectsAnInvalidIban() {
        given().contentType(ContentType.JSON)
                .body(Map.of("iban", "IT00X0542811101000000123456", "holder", "x", "initialBalance", new BigDecimal("1.00")))
                .when().post("/api/accounts")
                .then().statusCode(400)
                .body("violations.field", hasItem(endsWith("iban")))
                .body("violations.message", hasItem("must be a valid IBAN"));
    }

    @Test
    void rejectsMissingFields() {
        Map<String, Object> body = new HashMap<>();
        body.put("holder", " ");
        body.put("initialBalance", new BigDecimal("-1.00"));
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/accounts")
                .then().statusCode(400)
                .body("violations.size()", greaterThanOrEqualTo(3));
    }

    @Test
    void rejectsADuplicateIban() {
        String iban = Ibans.next();
        open(iban, "First", "0.00");
        given().contentType(ContentType.JSON)
                .body(Map.of("iban", iban, "holder", "Second", "initialBalance", new BigDecimal("0.00")))
                .when().post("/api/accounts")
                .then().statusCode(409)
                .body("message", containsString("already exists"));
    }

    @Test
    void unknownAccountIs404() {
        given().when().get("/api/accounts/{iban}", Ibans.next())
                .then().statusCode(404)
                .body("status", is(404))
                .body("message", containsString("No account"));
    }

    static void open(String iban, String holder, String initialBalance) {
        given().contentType(ContentType.JSON)
                .body(Map.of("iban", iban, "holder", holder, "initialBalance", new BigDecimal(initialBalance)))
                .when().post("/api/accounts")
                .then().statusCode(201);
    }
}
