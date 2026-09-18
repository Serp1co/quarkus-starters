package it.bancaditalia.quarkus.poc.remote.caller.stub;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import it.bancaditalia.quarkus.poc.remote.api.grpc.CounterpartyRegistry;
import it.bancaditalia.quarkus.poc.remote.api.grpc.CounterpartyReply;
import it.bancaditalia.quarkus.poc.remote.api.grpc.FindRequest;
import it.bancaditalia.quarkus.poc.remote.api.grpc.RegisterRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import java.util.List;
import java.util.TreeSet;

/** The provider's gRPC service, stubbed on the caller's unified test port: records the identity the metadata carried. */
@GrpcService
public class StubCounterpartyGrpc implements CounterpartyRegistry {

    @Inject
    SecurityIdentity identity;

    @Override
    @RolesAllowed({ "registry-reader", "registry-writer" })
    public Uni<CounterpartyReply> find(FindRequest request) {
        StubCounterpartyResource.CALLS.add(new StubCounterpartyResource.Call("grpc-find", identity.getPrincipal().getName(),
                List.copyOf(new TreeSet<>(identity.getRoles())), null));
        if (!"IT0001".equals(request.getCode())) {
            return Uni.createFrom().failure(Status.NOT_FOUND.asRuntimeException());
        }
        return Uni.createFrom().item(CounterpartyReply.newBuilder().setCode("IT0001").setName("Banca di Prova").setCountry("IT").setStatus("ACTIVE").build());
    }

    @Override
    @RolesAllowed("registry-writer")
    public Uni<CounterpartyReply> register(RegisterRequest request) {
        return Uni.createFrom().failure(Status.UNIMPLEMENTED.asRuntimeException());
    }
}
