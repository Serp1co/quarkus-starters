package it.bancaditalia.quarkus.poc.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;

/**
 * Reference data is served from the second-level cache: a lookup in a new session runs no SQL. Each lookup runs
 * in its own transaction, hence its own session: in one session the second call would be answered by the
 * persistence context (first-level cache), which proves nothing.
 */
@QuarkusTest
class ReferenceDataCacheTest {

    @Inject
    RegistryService registry;

    @Inject
    EntityManagerFactory emf;

    @Test
    void theSecondLookupIsACacheHit() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        assertEquals("Banca", QuarkusTransaction.requiringNew().call(() -> registry.type("BANK")).getDescription());
        long hitsAfterFirst = stats.getSecondLevelCacheHitCount(); // 0 on a cold cache (one put), 1 if warmed by another test
        long queriesAfterFirst = stats.getPrepareStatementCount();
        assertEquals("Banca", QuarkusTransaction.requiringNew().call(() -> registry.type("BANK")).getDescription());
        assertEquals(hitsAfterFirst + 1, stats.getSecondLevelCacheHitCount(),
                "second lookup served by the cache (puts " + stats.getSecondLevelCachePutCount() + ")");
        assertEquals(queriesAfterFirst, stats.getPrepareStatementCount(), "no SQL for the second lookup");
    }
}
