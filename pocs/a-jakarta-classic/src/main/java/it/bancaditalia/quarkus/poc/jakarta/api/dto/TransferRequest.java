package it.bancaditalia.quarkus.poc.jakarta.api.dto;

import it.bancaditalia.quarkus.poc.jakarta.validation.Iban;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransferRequest(
        @NotNull @Iban String debtorIban,
        @NotNull @Iban String creditorIban,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Size(max = 140) String reference) {
}
