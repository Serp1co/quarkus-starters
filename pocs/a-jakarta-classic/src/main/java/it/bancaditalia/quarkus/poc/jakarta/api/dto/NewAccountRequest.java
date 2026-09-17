package it.bancaditalia.quarkus.poc.jakarta.api.dto;

import it.bancaditalia.quarkus.poc.jakarta.validation.Iban;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record NewAccountRequest(
        @NotNull @Iban String iban,
        @NotBlank @Size(max = 120) String holder,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal initialBalance) {
}
