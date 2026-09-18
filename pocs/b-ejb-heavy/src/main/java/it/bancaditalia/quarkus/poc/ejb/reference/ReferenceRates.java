package it.bancaditalia.quarkus.poc.ejb.reference;

import io.quarkus.arc.Lock;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.jboss.logging.Logger;

/**
 * On EAP: {@code @Singleton @Startup @Lock(READ)} with {@code @Lock(WRITE)} on the refresh and a {@code @Schedule}
 * to refresh. Here: an {@code @ApplicationScoped} bean loaded on {@link StartupEvent}, ArC's {@link Lock} for the
 * same read/write semantics, and {@code @Scheduled} for the refresh. No container-managed concurrency, same behaviour.
 */
@ApplicationScoped
@Lock(Lock.Type.READ)
public class ReferenceRates {

    private static final Logger LOG = Logger.getLogger(ReferenceRates.class);

    private final Map<String, BigDecimal> rates = new HashMap<>();
    private Instant loadedAt;

    void onStart(@Observes StartupEvent event) {
        refresh();
    }

    /** Hourly, on the node the cluster elects; in-memory data, so every node refreshes its own copy (identity per node). */
    @Scheduled(cron = "0 0 * * * ?", identity = "reference-rates-refresh")
    @Lock(Lock.Type.WRITE)
    public void refresh() {
        // the real thing reads the ECB feed; the POC keeps a fixed table
        rates.put("EUR", BigDecimal.ONE);
        rates.put("USD", new BigDecimal("1.0850"));
        rates.put("GBP", new BigDecimal("0.8420"));
        loadedAt = Instant.now();
        LOG.infov("reference rates loaded: {0} currencies", rates.size());
    }

    public BigDecimal rate(String currency) {
        BigDecimal rate = rates.get(currency);
        if (rate == null) {
            throw new NoSuchElementException("No rate for " + currency);
        }
        return rate;
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    public int size() {
        return rates.size();
    }
}
