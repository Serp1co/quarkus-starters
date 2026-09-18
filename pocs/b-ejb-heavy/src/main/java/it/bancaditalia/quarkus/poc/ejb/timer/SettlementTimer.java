package it.bancaditalia.quarkus.poc.ejb.timer;

import io.quarkus.scheduler.Scheduled;
import it.bancaditalia.quarkus.poc.ejb.service.SettlementService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * On EAP: {@code @Singleton} with {@code @Schedule(second="*&#47;10", persistent=true)} and an HA-singleton policy so
 * that one node runs it. Here: {@code @Scheduled} on Quartz with a JDBC store in cluster mode (bdi-scheduler):
 * the trigger is a row in the database, one node acquires it per interval, a node that dies mid-run leaves a
 * misfire the others recover. The interval is a config expression, so the platform tunes it per environment.
 */
@ApplicationScoped
public class SettlementTimer {

    @Inject
    SettlementService settlement;

    @Scheduled(every = "{settlement.interval}", identity = "settlement", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void settle() {
        settlement.runNow();
    }
}
