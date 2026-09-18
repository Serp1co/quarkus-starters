package it.bancaditalia.quarkus.poc.ejb.service;

import it.bancaditalia.quarkus.poc.ejb.domain.Instruction;
import it.bancaditalia.quarkus.poc.ejb.repository.InstructionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Was {@code @Stateless}: the same class with {@code @ApplicationScoped} and {@code @Transactional}. */
@ApplicationScoped
public class InstructionService {

    @Inject
    InstructionRepository instructions;

    @Transactional
    public Instruction submit(String reference, BigDecimal amount) {
        Instruction instruction = new Instruction(reference, amount);
        instructions.persist(instruction);
        return instruction;
    }

    public Optional<Instruction> find(long id) {
        return instructions.findById(id);
    }

    public List<Instruction> list() {
        return instructions.findAll();
    }
}
