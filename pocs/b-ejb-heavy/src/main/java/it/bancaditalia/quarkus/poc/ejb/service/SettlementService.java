package it.bancaditalia.quarkus.poc.ejb.service;

import it.bancaditalia.quarkus.poc.ejb.config.SettlementConfig;
import it.bancaditalia.quarkus.poc.ejb.domain.SettlementRun;
import it.bancaditalia.quarkus.poc.ejb.repository.InstructionRepository;
import it.bancaditalia.quarkus.poc.ejb.repository.SettlementRunRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * The settlement run: the run record committed first, one REQUIRES_NEW transaction per batch, the totals committed
 * last. A batch that fails is rolled back and reported; the run and the other batches are committed. The method
 * itself is not transactional: on EAP the same orchestration inside a REQUIRED method would have failed on the
 * foreign key from the batch to the still uncommitted run row.
 */
@ApplicationScoped
public class SettlementService {

    private static final Logger LOG = Logger.getLogger(SettlementService.class);

    @Inject
    InstructionRepository instructions;

    @Inject
    SettlementRunRepository runs;

    @Inject
    SettlementBatch batch;

    @Inject
    FailureNotifier notifier;

    @Inject
    SettlementConfig config;

    /** The XA recovery node name doubles as the name of this instance in the run record. */
    @ConfigProperty(name = "quarkus.transaction-manager.node-name")
    String node;

    public SettlementRun runNow() {
        SettlementRun run = runs.start(node);
        List<Long> pending = instructions.pendingIds();
        int settled = 0;
        int rejected = 0;
        int failed = 0;
        for (int from = 0; from < pending.size(); from += config.batchSize()) {
            List<Long> ids = pending.subList(from, Math.min(from + config.batchSize(), pending.size()));
            try {
                SettlementBatch.Outcome outcome = batch.settle(ids, run.getId());
                settled += outcome.settled();
                rejected += outcome.rejected();
            } catch (RuntimeException e) {
                if (ids.size() == 1) {
                    failed++;
                    notifier.notifyFailure(run.getId(), ids, e.getMessage());
                    continue;
                }
                // A poison instruction must not block the batch forever: retry one instruction per transaction,
                // so that only the poison one stays pending and gets reported. On EAP: the same loop, by hand.
                LOG.warnv("run {0}: batch of {1} failed ({2}), retrying one by one", run.getId(), ids.size(), e.getMessage());
                for (Long id : ids) {
                    try {
                        SettlementBatch.Outcome outcome = batch.settle(List.of(id), run.getId());
                        settled += outcome.settled();
                        rejected += outcome.rejected();
                    } catch (RuntimeException single) {
                        failed++;
                        notifier.notifyFailure(run.getId(), List.of(id), single.getMessage());
                    }
                }
            }
        }
        run = runs.finish(run.getId(), settled, rejected, failed);
        LOG.infov("settlement run {0} on {1}: {2} settled, {3} rejected, {4} batches failed", run.getId(), node,
                run.getSettled(), run.getRejected(), run.getFailed());
        return run;
    }

    public List<SettlementRun> runs() {
        return runs.findAll();
    }
}
