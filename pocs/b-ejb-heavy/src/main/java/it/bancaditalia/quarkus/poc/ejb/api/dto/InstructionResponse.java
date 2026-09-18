package it.bancaditalia.quarkus.poc.ejb.api.dto;

import it.bancaditalia.quarkus.poc.ejb.domain.Instruction;
import java.math.BigDecimal;
import java.time.Instant;

public record InstructionResponse(long id, String reference, BigDecimal amount, String status, Instant createdAt,
        Instant settledAt, Long runId, String failureReason) {

    public static InstructionResponse of(Instruction i) {
        return new InstructionResponse(i.getId(), i.getReference(), i.getAmount(), i.getStatus().name(), i.getCreatedAt(),
                i.getSettledAt(), i.getRunId(), i.getFailureReason());
    }
}
