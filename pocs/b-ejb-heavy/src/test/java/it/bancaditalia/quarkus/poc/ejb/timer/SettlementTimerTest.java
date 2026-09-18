package it.bancaditalia.quarkus.poc.ejb.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import it.bancaditalia.quarkus.poc.ejb.repository.SettlementRunRepository;
import it.bancaditalia.quarkus.poc.ejb.service.SettlementService;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The timer is a Quartz trigger persisted in the database (clustered JDBC store), and it fires. */
@QuarkusTest
class SettlementTimerTest {

    @Inject
    SettlementService settlement;

    @Inject
    SettlementRunRepository runs;

    @Test
    void theTimerFiresFromTheDatabaseStore() throws Exception {
        assertTrue(runs.quartzJobs() >= 2, "settlement and reference-rates jobs persisted in qrtz_job_details");
        assertFalse(runs.quartzInstances().isEmpty(), "this instance registered in qrtz_scheduler_state");

        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (settlement.runs().isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(250); // %test interval is 2s
        }
        assertFalse(settlement.runs().isEmpty(), "the settlement timer ran at least once within 15s");
        assertEquals("quarkus", settlement.runs().get(0).getNode()); // default XA node name in the inner loop
    }
}
