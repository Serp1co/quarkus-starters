package it.bancaditalia.quarkus.poc.security.domain;

import it.bancaditalia.quarkus.poc.security.config.DeskConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class RequestStore {

    @Inject
    DeskConfig config;

    private final Map<Long, AuthorizationRequest> requests = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public AuthorizationRequest file(String applicant, String subject, String by) {
        long pending = requests.values().stream().filter(r -> r.status() == AuthorizationRequest.Status.PENDING).count();
        if (pending >= config.maxPending()) {
            throw new IllegalStateException("too many pending requests (" + pending + "), decide some first");
        }
        long id = ids.incrementAndGet();
        AuthorizationRequest request = new AuthorizationRequest(id, applicant, subject, by, Instant.now(),
                AuthorizationRequest.Status.PENDING, null, null, null);
        requests.put(id, request);
        return request;
    }

    public Optional<AuthorizationRequest> find(long id) {
        return Optional.ofNullable(requests.get(id));
    }

    public List<AuthorizationRequest> all() {
        return requests.values().stream().sorted(Comparator.comparingLong(AuthorizationRequest::id)).toList();
    }

    public Optional<AuthorizationRequest> decide(long id, AuthorizationRequest.Status decision, String by, String note) {
        return Optional.ofNullable(requests.computeIfPresent(id, (k, r) -> r.decide(decision, by, note)));
    }
}
