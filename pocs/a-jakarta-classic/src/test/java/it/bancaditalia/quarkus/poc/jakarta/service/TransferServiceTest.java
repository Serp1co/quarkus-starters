package it.bancaditalia.quarkus.poc.jakarta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import it.bancaditalia.quarkus.poc.jakarta.Ibans;
import it.bancaditalia.quarkus.poc.jakarta.config.LedgerConfig;
import it.bancaditalia.quarkus.poc.jakarta.domain.InsufficientFundsException;
import it.bancaditalia.quarkus.poc.jakarta.domain.Transfer;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises the CDI beans directly: no HTTP, only Narayana and Hibernate ORM. */
@QuarkusTest
class TransferServiceTest {

    @Inject
    TransferService transfers;

    @Inject
    AccountService accounts;

    @Inject
    LedgerConfig ledger;

    @Test
    void bindsTheInnerLoopConfiguration() {
        assertEquals("EUR", ledger.currency());
        assertMoney("1000.00", ledger.transfer().maxAmount());
        assertFalse(ledger.transfer().requireReference());
        assertEquals(List.of("IT66X0100503200000012345678"), ledger.transfer().blockedIbans().orElseThrow());
    }

    @Test
    void rollsBackTheWholeTransactionWhenTheDebitFails() {
        String debtor = Ibans.next();
        String creditor = Ibans.next();
        accounts.open(debtor, "Debtor", new BigDecimal("10.00"));
        accounts.open(creditor, "Creditor", new BigDecimal("0.00"));

        assertThrows(InsufficientFundsException.class,
                () -> transfers.execute(debtor, creditor, new BigDecimal("10.01"), null));

        assertMoney("0.00", accounts.get(creditor).getBalance()); // the credit was undone
        assertMoney("10.00", accounts.get(debtor).getBalance());
        assertTrue(transfers.forAccount(debtor).isEmpty()); // the persisted row was undone
    }

    @Test
    void commitsWhenTheDebitSucceeds() {
        String debtor = Ibans.next();
        String creditor = Ibans.next();
        accounts.open(debtor, "Debtor", new BigDecimal("10.00"));
        accounts.open(creditor, "Creditor", new BigDecimal("0.00"));

        Transfer transfer = transfers.execute(debtor, creditor, new BigDecimal("4.00"), "lunch");

        assertMoney("6.00", accounts.get(debtor).getBalance());
        assertMoney("4.00", accounts.get(creditor).getBalance());
        assertEquals(List.of(transfer.getId()), transfers.forAccount(creditor).stream().map(Transfer::getId).toList());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
