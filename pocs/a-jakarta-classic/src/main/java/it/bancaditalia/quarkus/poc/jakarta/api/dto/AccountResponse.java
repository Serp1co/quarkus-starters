package it.bancaditalia.quarkus.poc.jakarta.api.dto;

import it.bancaditalia.quarkus.poc.jakarta.domain.Account;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(String iban, String holder, BigDecimal balance, String currency, Instant createdAt) {

    public static AccountResponse of(Account account, String currency) {
        return new AccountResponse(account.getIban(), account.getHolder(), account.getBalance(), currency,
                account.getCreatedAt());
    }
}
