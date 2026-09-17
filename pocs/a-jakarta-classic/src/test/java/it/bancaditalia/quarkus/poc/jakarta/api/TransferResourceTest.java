package it.bancaditalia.quarkus.poc.jakarta.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import it.bancaditalia.quarkus.poc.jakarta.Ibans;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TransferResourceTest {

    /** Listed in %test.ledger.transfer.blocked-ibans. */
    static final String BLOCKED_IBAN = "IT66X0100503200000012345678";

    String debtor;
    String creditor;

    @BeforeEach
    void twoAccounts() {
        debtor = Ibans.next();
        creditor = Ibans.next();
        AccountResourceTest.open(debtor, "Debtor", "500.00");
        AccountResourceTest.open(creditor, "Creditor", "0.00");
    }

    @Test
    void movesMoneyInOneTransaction() {
        int id = given().contentType(ContentType.JSON)
                .body(transfer(debtor, creditor, "250.00", "rent"))
                .when().post("/api/transfers")
                .then().statusCode(201)
                .header("Location", containsString("/api/transfers/"))
                .body("debtorIban", is(debtor))
                .body("creditorIban", is(creditor))
                .body("amount", is(250.0f))
                .body("currency", is("EUR"))
                .body("reference", is("rent"))
                .extract().path("id");

        assertBalance(debtor, 250.0f);
        assertBalance(creditor, 250.0f);

        given().when().get("/api/transfers/{id}", id)
                .then().statusCode(200)
                .body("debtorIban", is(debtor));

        given().when().get("/api/accounts/{iban}/transfers", creditor)
                .then().statusCode(200)
                .body("size()", is(1))
                .body("id", hasItem(id));
    }

    @Test
    void insufficientFundsRollsEverythingBack() {
        given().contentType(ContentType.JSON)
                .body(transfer(debtor, creditor, "500.01", null))
                .when().post("/api/transfers")
                .then().statusCode(422)
                .body("message", containsString("available"));

        // the credit and the transfer row written before the failing debit are gone
        assertBalance(debtor, 500.0f);
        assertBalance(creditor, 0.0f);
        given().when().get("/api/accounts/{iban}/transfers", creditor)
                .then().statusCode(200)
                .body("size()", is(0));
    }

    @Test
    void enforcesTheConfiguredMaximum() {
        AccountResourceTest.open(Ibans.next(), "unused", "0.00");
        given().contentType(ContentType.JSON)
                .body(transfer(debtor, creditor, "1000.01", null))
                .when().post("/api/transfers")
                .then().statusCode(422);
        // above the %test maximum of 1000.00 even though funds would suffice on a richer account
        String rich = Ibans.next();
        AccountResourceTest.open(rich, "Rich", "5000.00");
        given().contentType(ContentType.JSON)
                .body(transfer(rich, creditor, "1000.01", null))
                .when().post("/api/transfers")
                .then().statusCode(422)
                .body("message", containsString("maximum of 1000.00 EUR"));
        assertBalance(rich, 5000.0f);
    }

    @Test
    void enforcesTheBlockedList() {
        given().contentType(ContentType.JSON)
                .body(transfer(debtor, BLOCKED_IBAN, "1.00", null))
                .when().post("/api/transfers")
                .then().statusCode(422)
                .body("message", containsString("blocked"));
        assertBalance(debtor, 500.0f);
    }

    @Test
    void rejectsATransferToTheSameAccount() {
        given().contentType(ContentType.JSON)
                .body(transfer(debtor, debtor, "1.00", null))
                .when().post("/api/transfers")
                .then().statusCode(422)
                .body("message", containsString("same account"));
    }

    @Test
    void unknownCreditorIs404() {
        given().contentType(ContentType.JSON)
                .body(transfer(debtor, Ibans.next(), "1.00", null))
                .when().post("/api/transfers")
                .then().statusCode(404);
        assertBalance(debtor, 500.0f);
    }

    @Test
    void validatesTheRequest() {
        given().contentType(ContentType.JSON)
                .body(transfer(debtor, "not-an-iban", "-1.00", null))
                .when().post("/api/transfers")
                .then().statusCode(400)
                .body("violations.size()", is(2));
    }

    static Map<String, Object> transfer(String debtor, String creditor, String amount, String reference) {
        Map<String, Object> body = new HashMap<>();
        body.put("debtorIban", debtor);
        body.put("creditorIban", creditor);
        body.put("amount", new BigDecimal(amount));
        body.put("reference", reference);
        return body;
    }

    static void assertBalance(String iban, float expected) {
        given().when().get("/api/accounts/{iban}", iban)
                .then().statusCode(200)
                .body("balance", is(expected));
    }
}
