package it.bancaditalia.quarkus.poc.jakarta.api.dto;

import it.bancaditalia.quarkus.poc.jakarta.domain.Transfer;
import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(long id, String debtorIban, String creditorIban, BigDecimal amount, String currency,
        String reference, Instant executedAt) {

    public static TransferResponse of(Transfer transfer, String currency) {
        return new TransferResponse(transfer.getId(), transfer.getDebtor().getIban(),
                transfer.getCreditor().getIban(), transfer.getAmount(), currency, transfer.getReference(),
                transfer.getExecutedAt());
    }
}
