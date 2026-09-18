package it.bancaditalia.quarkus.poc.remote.provider.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.grpc.GrpcClientUtils;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import it.bancaditalia.quarkus.poc.remote.api.grpc.CounterpartyRegistry;
import it.bancaditalia.quarkus.poc.remote.api.grpc.CounterpartyReply;
import it.bancaditalia.quarkus.poc.remote.api.grpc.FindRequest;
import it.bancaditalia.quarkus.poc.remote.api.grpc.RegisterRequest;
import it.bancaditalia.quarkus.test.oidc.RhbkLikeRealm;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The typed contract, called through a real gRPC client on the unified test port, bearer token in the metadata. */
@QuarkusTest
@WithTestResource(RhbkLikeRealm.class)
class CounterpartyGrpcTest {

    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @GrpcClient("counterparty")
    CounterpartyRegistry client;

    private CounterpartyRegistry as(String user, Set<String> roles) {
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, "Bearer " + RhbkLikeRealm.token(user, roles));
        return GrpcClientUtils.attachHeaders(client, headers);
    }

    @Test
    void findRequiresARoleAndAnswersTheContract() {
        StatusRuntimeException anonymous = assertThrows(StatusRuntimeException.class,
                () -> client.find(FindRequest.newBuilder().setCode("IT0001").build()).await().atMost(Duration.ofSeconds(5)));
        // Quarkus answers an anonymous call to a @RolesAllowed gRPC method with PERMISSION_DENIED (no challenge on gRPC)
        assertEquals(Status.Code.PERMISSION_DENIED, anonymous.getStatus().getCode());

        CounterpartyReply reply = as("alice", Set.of("app-readers")).find(FindRequest.newBuilder().setCode("IT0001").build())
                .await().atMost(Duration.ofSeconds(5));
        assertEquals("Banca di Prova", reply.getName());

        StatusRuntimeException missing = assertThrows(StatusRuntimeException.class,
                () -> as("alice", Set.of("app-readers")).find(FindRequest.newBuilder().setCode("XX9999").build()).await().atMost(Duration.ofSeconds(5)));
        assertEquals(Status.Code.NOT_FOUND, missing.getStatus().getCode());
    }

    @Test
    void registerIsForServicesAndIdempotent() {
        String key = UUID.randomUUID().toString();
        RegisterRequest request = RegisterRequest.newBuilder().setCode("PT" + key.substring(0, 4).toUpperCase())
                .setName("Banco Teste").setCountry("PT").setIdempotencyKey(key).build();
        StatusRuntimeException forbidden = assertThrows(StatusRuntimeException.class,
                () -> as("alice", Set.of("app-readers")).register(request).await().atMost(Duration.ofSeconds(5)));
        assertEquals(Status.Code.PERMISSION_DENIED, forbidden.getStatus().getCode());

        CounterpartyReply first = as("svc-reports", Set.of("app-services")).register(request).await().atMost(Duration.ofSeconds(5));
        assertTrue(first.getCreated());
        CounterpartyReply retry = as("svc-reports", Set.of("app-services")).register(request).await().atMost(Duration.ofSeconds(5));
        assertFalse(retry.getCreated());
        assertEquals(first.getCode(), retry.getCode());
    }
}
