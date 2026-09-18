package it.bancaditalia.quarkus.poc.data.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IntermediaryResourceTest {

    private static String randomAbi() {
        return String.format("%05d", ThreadLocalRandom.current().nextInt(10000, 89999));
    }

    private static Map<String, Object> address(String city, String province) {
        return Map.of("street", "Via Nazionale 91", "city", city, "province", province, "postalCode", "00184");
    }

    private static String register(String abi, String name, String type) {
        given().contentType(ContentType.JSON)
                .body(Map.of("abi", abi, "name", name, "type", type, "headquarters", address("Roma", "RM")))
                .when().post("/api/intermediaries")
                .then().statusCode(201)
                .header("Location", containsString("/api/intermediaries/" + abi))
                .body("status", is("ACTIVE"))
                .body("version", is(0))
                .body("id", notNullValue());
        return abi;
    }

    @Test
    void registersReadsAndSearches() {
        String abi = register(randomAbi(), "Banca Test " + randomAbi(), "BANK");
        given().when().get("/api/intermediaries/{abi}", abi)
                .then().statusCode(200)
                .body("type", is("BANK"))
                .body("headquarters.city", is("Roma"));
        given().queryParam("q", "banca test").queryParam("size", 2).queryParam("sort", "name")
                .when().get("/api/intermediaries")
                .then().statusCode(200)
                .header("X-Total-Count", notNullValue())
                .header("X-Page-Size", is("2"))
                .body("size()", greaterThanOrEqualTo(1));
        given().queryParam("size", 50)   // %test caps pages at 5 (registry.max-page-size)
                .when().get("/api/intermediaries")
                .then().statusCode(200)
                .header("X-Page-Size", is("5"));
        given().queryParam("sort", "id; drop table intermediary")
                .when().get("/api/intermediaries")
                .then().statusCode(400);
    }

    @Test
    void refusesWhatTheRulesRefuse() {
        given().contentType(ContentType.JSON)
                .body(Map.of("abi", "ABCDE", "name", "Bad ABI", "type", "BANK", "headquarters", address("Roma", "RM")))
                .when().post("/api/intermediaries")
                .then().statusCode(422)
                .body("detail", containsString("does not match"));
        String abi = register(randomAbi(), "Twice", "SIM");
        given().contentType(ContentType.JSON)
                .body(Map.of("abi", abi, "name", "Twice", "type", "SIM", "headquarters", address("Roma", "RM")))
                .when().post("/api/intermediaries")
                .then().statusCode(422)
                .body("detail", containsString("already registered"));
        given().contentType(ContentType.JSON)
                .body(Map.of("abi", randomAbi(), "name", "", "type", "BANK", "headquarters", address("Roma", "RM")))
                .when().post("/api/intermediaries")
                .then().statusCode(400); // Bean Validation
        given().when().get("/api/intermediaries/{abi}", "00000")
                .then().statusCode(404);
    }

    @Test
    void optimisticLockingAndHistory() {
        String abi = register(randomAbi(), "Banca Storica", "BANK");
        given().contentType(ContentType.JSON)
                .body(Map.of("status", "SUSPENDED", "version", 0))
                .when().put("/api/intermediaries/{abi}/status", abi)
                .then().statusCode(200)
                .body("status", is("SUSPENDED"))
                .body("version", is(1));
        // a second client that still holds version 0 must not silently overwrite
        given().contentType(ContentType.JSON)
                .body(Map.of("status", "CANCELLED", "version", 0))
                .when().put("/api/intermediaries/{abi}/status", abi)
                .then().statusCode(409)
                .body("detail", containsString("changed since it was read"));
        given().when().get("/api/intermediaries/{abi}", abi)
                .then().statusCode(200)
                .body("status", is("SUSPENDED"));
        // Envers: the insert and the update are two revisions with the state of the row at each
        given().when().get("/api/intermediaries/{abi}/history", abi)
                .then().statusCode(200)
                .body("size()", is(2))
                .body("type", contains("ADD", "MOD"))
                .body("status", contains("ACTIVE", "SUSPENDED"))
                .body("[1].at", notNullValue());
    }

    @Test
    void branchesActiveRecordAndNativeReport() {
        String abi = register(randomAbi(), "Banca con Filiali", "BANK");
        for (String[] branch : new String[][] { { "001", "Torino", "TO" }, { "002", "Catania", "CT" }, { "003", "Torino", "TO" } }) {
            given().contentType(ContentType.JSON)
                    .body(Map.of("code", branch[0], "address", address(branch[1], branch[2])))
                    .when().post("/api/intermediaries/{abi}/branches", abi)
                    .then().statusCode(201)
                    .body("id", notNullValue());
        }
        given().when().get("/api/intermediaries/{abi}/branches", abi)
                .then().statusCode(200)
                .body("code", contains("001", "002", "003"));
        given().when().get("/api/intermediaries/{abi}/branches/by-province", abi)
                .then().statusCode(200)
                .body("province", contains("CT", "TO"))
                .body("branches", contains(1, 2));
    }

    @Test
    void referenceDataAndOverview() {
        given().when().get("/api/registry/types")
                .then().statusCode(200)
                .body("code", hasItem("BANK"))
                .body("size()", is(5));
        given().when().get("/api/registry")
                .then().statusCode(200)
                .body("supervisor", containsString("Vigilanza"))
                .body("byStatus.ACTIVE", notNullValue());
    }
}
