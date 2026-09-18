package it.bancaditalia.quarkus.poc.remote.provider.grpc;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import it.bancaditalia.quarkus.poc.remote.api.Counterparty;
import it.bancaditalia.quarkus.poc.remote.api.grpc.CounterpartyRegistry;
import it.bancaditalia.quarkus.poc.remote.api.grpc.CounterpartyReply;
import it.bancaditalia.quarkus.poc.remote.api.grpc.FindRequest;
import it.bancaditalia.quarkus.poc.remote.api.grpc.RegisterRequest;
import it.bancaditalia.quarkus.poc.remote.provider.registry.CounterpartyRegistryBean;
import it.bancaditalia.quarkus.poc.remote.provider.registry.DuplicateCounterpartyException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

/**
 * The typed contract of design note 4.2: the same bean behind the .proto, served by the unified HTTP server,
 * so the bearer token in the call metadata is verified like on REST and {@code @RolesAllowed} applies.
 */
@GrpcService
public class CounterpartyGrpcService implements CounterpartyRegistry {

    @Inject
    CounterpartyRegistryBean registry;

    @Override
    @RolesAllowed({ "registry-reader", "registry-writer" })
    public Uni<CounterpartyReply> find(FindRequest request) {
        return Uni.createFrom().item(() -> registry.find(request.getCode())
                .map(c -> reply(c, false))
                .orElseThrow(() -> Status.NOT_FOUND.withDescription("no counterparty " + request.getCode()).asRuntimeException()));
    }

    @Override
    @RolesAllowed("registry-writer")
    public Uni<CounterpartyReply> register(RegisterRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                CounterpartyRegistryBean.Registration registration = registry.register(request.getIdempotencyKey(),
                        request.getCode(), request.getName(), request.getCountry());
                return reply(registration.counterparty(), registration.created());
            } catch (DuplicateCounterpartyException e) {
                throw Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    private static CounterpartyReply reply(Counterparty c, boolean created) {
        return CounterpartyReply.newBuilder().setCode(c.code()).setName(c.name()).setCountry(c.country())
                .setStatus(c.status()).setCreated(created).build();
    }
}
