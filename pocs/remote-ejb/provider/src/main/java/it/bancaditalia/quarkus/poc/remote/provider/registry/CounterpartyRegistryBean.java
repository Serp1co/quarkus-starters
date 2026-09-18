package it.bancaditalia.quarkus.poc.remote.provider.registry;

import it.bancaditalia.quarkus.poc.remote.api.Counterparty;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bean that was {@code @Stateless @Remote(CounterpartyService.class)} on EAP. Unchanged in what it does;
 * what changed is who calls it: the JAX-RS facade and the gRPC service, not a remote proxy. The idempotency
 * key is the one addition: without transaction propagation across the wire, the caller retries, and a retry
 * must not register twice.
 */
@ApplicationScoped
public class CounterpartyRegistryBean {

    public record Registration(Counterparty counterparty, boolean created) {
    }

    private final Map<String, Counterparty> byCode = new ConcurrentHashMap<>();
    private final Map<String, String> codeByIdempotencyKey = new ConcurrentHashMap<>();

    public CounterpartyRegistryBean() {
        byCode.put("IT0001", new Counterparty("IT0001", "Banca di Prova", "IT", "ACTIVE"));
        byCode.put("DE0002", new Counterparty("DE0002", "Musterbank", "DE", "ACTIVE"));
    }

    public Optional<Counterparty> find(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public Registration register(String idempotencyKey, String code, String name, String country) {
        String seen = codeByIdempotencyKey.get(idempotencyKey);
        if (seen != null) {
            return new Registration(byCode.get(seen), false); // the retry of an operation already applied
        }
        Counterparty created = new Counterparty(code, name, country, "ACTIVE");
        if (byCode.putIfAbsent(code, created) != null) {
            throw new DuplicateCounterpartyException(code);
        }
        codeByIdempotencyKey.put(idempotencyKey, code);
        return new Registration(created, true);
    }
}
