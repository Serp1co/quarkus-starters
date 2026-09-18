package it.bancaditalia.quarkus.poc.ejb.repository;

import it.bancaditalia.quarkus.poc.ejb.domain.Instruction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class InstructionRepository {

    @Inject
    EntityManager em;

    public void persist(Instruction instruction) {
        em.persist(instruction);
    }

    public Optional<Instruction> findById(long id) {
        return Optional.ofNullable(em.find(Instruction.class, id));
    }

    /** Pessimistic lock: two settlement nodes must never settle the same instruction (EAP: the same SELECT FOR UPDATE). */
    public Optional<Instruction> lockById(long id) {
        return Optional.ofNullable(em.find(Instruction.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public List<Long> pendingIds() {
        return em.createNamedQuery(Instruction.PENDING_IDS, Long.class).getResultList();
    }

    public List<Instruction> findAll() {
        return em.createNamedQuery(Instruction.FIND_ALL, Instruction.class).getResultList();
    }
}
