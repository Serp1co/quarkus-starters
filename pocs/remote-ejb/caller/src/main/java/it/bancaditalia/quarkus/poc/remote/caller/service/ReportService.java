package it.bancaditalia.quarkus.poc.remote.caller.service;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.security.identity.SecurityIdentity;
import it.bancaditalia.quarkus.poc.remote.api.Counterparty;
import it.bancaditalia.quarkus.poc.remote.api.CounterpartyServiceClient;
import it.bancaditalia.quarkus.poc.remote.api.CounterpartyUserClient;
import it.bancaditalia.quarkus.poc.remote.api.RegisterCounterparty;
import it.bancaditalia.quarkus.poc.remote.api.grpc.CounterpartyRegistry;
import it.bancaditalia.quarkus.poc.remote.api.grpc.CounterpartyReply;
import it.bancaditalia.quarkus.poc.remote.api.grpc.FindRequest;
import it.bancaditalia.quarkus.poc.remote.caller.domain.Report;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * What used to be {@code @EJB CounterpartyService registry;} with the transaction and the caller's identity
 * propagated by the container. Here (design note 4.2):
 * <ul>
 * <li>the lookup on behalf of the user goes through {@link CounterpartyUserClient}: the user's bearer token is
 * propagated, the registry authorizes the user;</li>
 * <li>the registration of an unknown counterparty is the application's own act (a batch would do it too), so it
 * goes through {@link CounterpartyServiceClient} with the application's client-credentials token;</li>
 * <li>there is no transaction across the wire: the report is stored first, the remote step is idempotent
 * (the report id is the Idempotency-Key) and retried, and the status says how far it got;</li>
 * <li>{@link #verify} is the same lookup over gRPC: the typed contract, the token propagated by the platform.</li>
 * </ul>
 */
@ApplicationScoped
public class ReportService {

    private static final Logger LOG = Logger.getLogger(ReportService.class);

    @Inject
    @RestClient
    CounterpartyUserClient asUser;

    @Inject
    @RestClient
    CounterpartyServiceClient asService;

    @GrpcClient("counterparty")
    CounterpartyRegistry typed;

    @Inject
    SecurityIdentity identity;

    private final Map<String, Report> reports = new ConcurrentHashMap<>();

    public Report file(String counterpartyCode, BigDecimal amount, String counterpartyName, String country) {
        Report report = new Report(UUID.randomUUID().toString(), counterpartyCode, amount, identity.getPrincipal().getName(),
                Instant.now(), Report.Status.PENDING_REGISTRY, null, null);
        reports.put(report.id(), report); // stored before any remote call: the local fact survives a remote failure
        return complete(report, counterpartyName, country);
    }

    /** The retry of the remote step: the same idempotency key (the report id), so nothing is registered twice. */
    public Optional<Report> retry(String id, String counterpartyName, String country) {
        return Optional.ofNullable(reports.get(id)).map(r -> complete(r, counterpartyName, country));
    }

    private Report complete(Report report, String counterpartyName, String country) {
        Report updated;
        try {
            Counterparty known = asUser.find(report.counterpartyCode());
            updated = report.with(Report.Status.COMPLETE, known.name(), "known to the registry");
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() != 404) {
                updated = report.with(Report.Status.REJECTED_BY_REGISTRY, null, "registry answered " + e.getResponse().getStatus());
            } else {
                updated = register(report, counterpartyName, country);
            }
        } catch (RuntimeException e) {
            LOG.warnf("registry unreachable for report %s: %s", report.id(), e.toString());
            updated = report.with(Report.Status.PENDING_REGISTRY, null, "registry unreachable: " + e.getClass().getSimpleName());
        }
        reports.put(updated.id(), updated);
        return updated;
    }

    private Report register(Report report, String counterpartyName, String country) {
        if (counterpartyName == null || country == null) {
            return report.with(Report.Status.PENDING_REGISTRY, null, "unknown to the registry; name and country needed to register it");
        }
        try {
            Counterparty created = asService.register(report.id(), new RegisterCounterparty(report.counterpartyCode(), counterpartyName, country));
            return report.with(Report.Status.COMPLETE, created.name(), "registered by " + "the application (service identity)");
        } catch (WebApplicationException e) {
            return report.with(Report.Status.REJECTED_BY_REGISTRY, null, "registration refused: " + e.getResponse().getStatus());
        }
    }

    /** The typed contract: the same lookup over gRPC, the caller's token propagated by bdi-config-grpc. */
    public CounterpartyReply verify(String id) {
        Report report = reports.get(id);
        if (report == null) {
            return null;
        }
        return typed.find(FindRequest.newBuilder().setCode(report.counterpartyCode()).build()).await().atMost(Duration.ofSeconds(5));
    }

    public Optional<Report> get(String id) {
        return Optional.ofNullable(reports.get(id));
    }

    public List<Report> all() {
        return reports.values().stream().sorted(Comparator.comparing(Report::filedAt)).toList();
    }
}
