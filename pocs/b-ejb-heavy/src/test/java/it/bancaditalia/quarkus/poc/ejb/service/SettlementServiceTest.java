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

        assertNotNull(run.getId());
        assertTrue(run.getSettled() >= 1);
        assertTrue(run.getRejected() >= 1);
        assertTrue(run.getFailed() >= 1);
        assertEquals(Instruction.Status.SETTLED, instructions.find(ok.getId()).orElseThrow().getStatus());
        assertEquals(Instruction.Status.REJECTED, instructions.find(big.getId()).orElseThrow().getStatus());
        assertEquals(Instruction.Status.PENDING, instructions.find(err.getId()).orElseThrow().getStatus()); // rolled back
        assertEquals(run.getId(), instructions.find(ok.getId()).orElseThrow().getRunId());

        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (notifier.sent().size() <= before && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertTrue(notifier.sent().stream().anyMatch(m -> m.contains("ERR-" + tag)), "asynchronous notification sent");
    }
}
