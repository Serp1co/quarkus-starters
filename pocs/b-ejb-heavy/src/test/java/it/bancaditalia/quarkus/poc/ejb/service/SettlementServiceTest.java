package it.bancaditalia.quarkus.poc.ejb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import it.bancaditalia.quarkus.poc.ejb.domain.Instruction;
import it.bancaditalia.quarkus.poc.ejb.domain.SettlementRun;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Transaction semantics of class B: REQUIRES_NEW batches, the outer run surviving a failing batch, async notification. */
@QuarkusTest
class SettlementServiceTest {

    @Inject
    InstructionService instructions;

    @Inject
    SettlementService settlement;

    @Inject
    FailureNotifier notifier;

    @Inject
    it.bancaditalia.quarkus.poc.ejb.service.SettlementBatch batch;

    @Inject
    it.bancaditalia.quarkus.poc.ejb.repository.SettlementRunRepository runs;

    @Test
    void aPoisonInstructionRollsBackItsBatchAndTheOthersAreRetriedAlone() {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Instruction ok1 = instructions.submit("OK1-" + tag, new BigDecimal("1.00"));
        Instruction err = instructions.submit("ERR-" + tag, new BigDecimal("1.00"));
        Instruction ok2 = instructions.submit("OK2-" + tag, new BigDecimal("1.00"));
        java.util.List<Long> ids = java.util.List.of(ok1.getId(), err.getId(), ok2.getId());

        // the whole batch rolls back: nothing settled, not even ok1 which was processed before the poison one
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> batch.settle(ids, 0L));
        assertEquals(Instruction.Status.PENDING, instructions.find(ok1.getId()).orElseThrow().getStatus());
    }

    @Test
    void aFailingBatchRollsBackAloneAndTheRunIsCommitted() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Instruction ok = instructions.submit("OK-" + tag, new BigDecimal("10.00"));
        Instruction big = instructions.submit("BIG-" + tag, new BigDecimal("5000.00")); // above the %test maximum
        Instruction err = instructions.submit("ERR-" + tag, new BigDecimal("10.00"));   // the failure prefix
        int before = notifier.sent().size();

        SettlementRun run = settlement.runNow(); // %test batch-size is 1: one transaction per instruction

        // The 2 s test timer may have taken some of the three before this run did (the pessimistic lock makes
        // the two runs share the work, never duplicate it): the outcomes below hold whichever run settled what.
        assertNotNull(run.getId());
        assertTrue(run.getFailed() >= 1, "the poison instruction fails every run it is offered to");
        Instruction settled = instructions.find(ok.getId()).orElseThrow();
        assertEquals(Instruction.Status.SETTLED, settled.getStatus());
        assertEquals(Instruction.Status.REJECTED, instructions.find(big.getId()).orElseThrow().getStatus());
        assertEquals(Instruction.Status.PENDING, instructions.find(err.getId()).orElseThrow().getStatus()); // rolled back
        SettlementRun settlingRun = runs.findAll().stream().filter(r -> r.getId().equals(settled.getRunId())).findFirst().orElseThrow();
        assertTrue(settlingRun.getSettled() >= 1, "the run that settled OK counted it");
        assertTrue(settlingRun.getFinishedAt() != null, "that run was committed although one of its batches failed");

        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (notifier.sent().size() <= before && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertTrue(notifier.sent().stream().anyMatch(m -> m.contains("ERR-" + tag)), "asynchronous notification sent");
    }
}
