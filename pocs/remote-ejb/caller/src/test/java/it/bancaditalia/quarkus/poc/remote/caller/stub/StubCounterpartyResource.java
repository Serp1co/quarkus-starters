package it.bancaditalia.quarkus.poc.remote.caller.stub;

import io.quarkus.security.identity.SecurityIdentity;
import it.bancaditalia.quarkus.poc.remote.api.Counterparty;
import it.bancaditalia.quarkus.poc.remote.api.RegisterCounterparty;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The provider, stubbed on the caller's own test port: records who called (the identity the token carried)
 * and the idempotency keys, so the tests can prove propagation and idempotent retries without a second process.
 */
@Path("/api/counterparties")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StubCounterpartyResource {

    public record Call(String operation, String principal, List<String> roles, String idempotencyKey) {
    }

    public static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final Map<String, Counterparty> BY_CODE = new ConcurrentHashMap<>(Map.of("IT0001", new Counterparty("IT0001", "Banca di Prova", "IT", "ACTIVE")));
    private static final Map<String, String> KEYS = new ConcurrentHashMap<>();

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/{code}")
    @RolesAllowed({ "registry-reader", "registry-writer" })
    public Counterparty find(@PathParam("code") String code) {
        CALLS.add(new Call("find", identity.getPrincipal().getName(), List.copyOf(new TreeSet<>(identity.getRoles())), null));
        Counterparty c = BY_CODE.get(code);
        if (c == null) {
            throw new NotFoundException();
        }
        return c;
    }

    @POST
    @RolesAllowed("registry-writer")
    public Response register(@HeaderParam("Idempotency-Key") String key, RegisterCounterparty request) {
        CALLS.add(new Call("register", identity.getPrincipal().getName(), List.copyOf(new TreeSet<>(identity.getRoles())), key));
        if (KEYS.containsKey(key)) {
            return Response.ok(BY_CODE.get(KEYS.get(key))).build();
        }
        Counterparty created = new Counterparty(request.code(), request.name(), request.country(), "ACTIVE");
        BY_CODE.put(request.code(), created);
        KEYS.put(key, request.code());
        return Response.status(201).entity(created).build();
    }
}
