package it.bancaditalia.quarkus.poc.ejb.repository;

import it.bancaditalia.quarkus.poc.ejb.domain.SettlementRun;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class SettlementRunRepository {

    @Inject
    EntityManager em;

    /**
     * REQUIRES_NEW on purpose: the run row must be committed before the batches (themselves REQUIRES_NEW) reference
     * it through the foreign key. An outer REQUIRED transaction would hold it uncommitted and every batch would fail
     * with a constraint violation: the classic EAP REQUIRES_NEW pitfall, reproduced and fixed here.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public SettlementRun start(String node) {
        SettlementRun run = new SettlementRun(node);
        em.persist(run);
        return run;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public SettlementRun finish(long runId, int settled, int rejected, int failed) {
        SettlementRun run = em.find(SettlementRun.class, runId);
        run.record(settled, rejected);
        for (int i = 0; i < failed; i++) {
            run.batchFailed();
        }
        run.finish();
        return run;
    }

    public List<SettlementRun> findAll() {
        return em.createNamedQuery(SettlementRun.FIND_ALL, SettlementRun.class).getResultList();
    }

    /** Proof that the timer lives in the database: the Quartz cluster rows (one per running instance). */
    public List<String> quartzInstances() {
        return em.createNativeQuery("select instance_name from qrtz_scheduler_state order by instance_name", String.class)
                .getResultList();
    }

    public long quartzJobs() {
        return ((Number) em.createNativeQuery("select count(*) from qrtz_job_details").getSingleResult()).longValue();
    }
}
