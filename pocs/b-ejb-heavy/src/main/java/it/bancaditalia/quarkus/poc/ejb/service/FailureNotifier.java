package it.bancaditalia.quarkus.poc.ejb.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

/**
 * On EAP: {@code @Asynchronous} on a session bean method. Here the MicroProfile {@link ManagedExecutor}: the caller
 * returns at once, the work runs on a managed thread with the CDI and transaction context propagated as configured.
 */
@ApplicationScoped
public class FailureNotifier {

    private static final Logger LOG = Logger.getLogger(FailureNotifier.class);

    @Inject
    ManagedExecutor executor;

    private final ConcurrentLinkedQueue<String> sent = new ConcurrentLinkedQueue<>();

    public CompletionStage<Void> notifyFailure(long runId, List<Long> batch, String reason) {
        return executor.runAsync(() -> {
            // the real thing talks to the operations desk (mail, ticket, chat); the POC records the message
            String message = "run " + runId + ": batch " + batch + " failed: " + reason;
            LOG.warn(message);
            sent.add(message);
        });
    }

    public List<String> sent() {
        return List.copyOf(sent);
    }
}
