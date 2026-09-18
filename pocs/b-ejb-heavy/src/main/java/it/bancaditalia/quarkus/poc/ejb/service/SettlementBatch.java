package it.bancaditalia.quarkus.poc.ejb.service;

import it.bancaditalia.quarkus.poc.ejb.config.SettlementConfig;
import it.bancaditalia.quarkus.poc.ejb.domain.Instruction;
import it.bancaditalia.quarkus.poc.ejb.domain.SettlementFailedException;
import it.bancaditalia.quarkus.poc.ejb.repository.InstructionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * On EAP: {@code @Stateless} with {@code @TransactionAttribute(REQUIRES_NEW)}. Here {@code @Transactional(REQUIRES_NEW)}
 * on a CDI bean: each batch commits or rolls back on its own, whatever happens to the run that called it.
 */
@ApplicationScoped
public class SettlementBatch {

    public record Outcome(int settled, int rejected) {
    }

    @Inject
    InstructionRepository instructions;

    @Inject
    SettlementConfig config;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Outcome settle(List<Long> ids, long runId) {
        int settled = 0;
        int rejected = 0;
        for (Long id : ids) {
            Instruction instruction = instructions.lockById(id).orElseThrow();
            if (instruction.getStatus() != Instruction.Status.PENDING) {
                continue; // another node got it first
            }
            if (instruction.getReference().startsWith(config.failurePrefix())) {
                throw new SettlementFailedException("Downstream failure while settling " + instruction.getReference());
            }
            if (instruction.getAmount().compareTo(config.maxInstructionAmount()) > 0) {
                instruction.reject(runId, "Amount above the maximum of " + config.maxInstructionAmount());
                rejected++;
            } else {
                instruction.settle(runId);
                settled++;
            }
        }
        return new Outcome(settled, rejected);
    }
}
