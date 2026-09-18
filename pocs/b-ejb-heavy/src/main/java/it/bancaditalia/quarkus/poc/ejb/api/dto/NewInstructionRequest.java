package it.bancaditalia.quarkus.poc.ejb.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record NewInstructionRequest(
        @NotBlank @Size(max = 35) String reference,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount) {
}
