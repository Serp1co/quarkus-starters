package it.bancaditalia.quarkus.poc.ejb.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InstructionResourceTest {

    @Test
    void submitsAndReadsAnInstruction() {
        String reference = "REF-" + UUID.randomUUID().toString().substring(0, 8);
        int id = given().contentType(ContentType.JSON)
                .body(Map.of("reference", reference, "amount", new BigDecimal("12.50")))
                .when().post("/api/instructions")
                .then().statusCode(201)
                .body("status", is("PENDING"))
                .extract().path("id");
        given().when().get("/api/instructions/{id}", id)
                .then().statusCode(200)
                .body("reference", is(reference))
                .body("amount", is(12.5f));
        given().when().get("/api/instructions")
                .then().statusCode(200)
                .body("reference", hasItem(reference));
    }

    @Test
    void validatesTheRequest() {
        given().contentType(ContentType.JSON)
                .body(Map.of("reference", "", "amount", new BigDecimal("-1")))
                .when().post("/api/instructions")
                .then().statusCode(400);
    }

    @Test
    void referenceRatesAreLoadedAtStartup() {
        given().when().get("/api/settlement/rates/usd")
                .then().statusCode(200)
                .body("currency", is("USD"))
                .body("rate", is(1.085f));
        given().when().get("/api/settlement/rates/xxx")
                .then().statusCode(404);
    }
}
