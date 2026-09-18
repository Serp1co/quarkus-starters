package it.bancaditalia.quarkus.poc.remote.provider.api;

import it.bancaditalia.quarkus.poc.remote.api.Counterparty;
import it.bancaditalia.quarkus.poc.remote.api.RegisterCounterparty;
import it.bancaditalia.quarkus.poc.remote.provider.registry.CounterpartyRegistryBean;
import it.bancaditalia.quarkus.poc.remote.provider.registry.DuplicateCounterpartyException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * The JAX-RS facade over the ex-remote bean: the default replacement of design note 4.2, and the same class
 * an EAP 8 host would deploy over the old EJBs during the transition. Authorization is the bean's roles, now
 * on the facade: the caller's token names the user, the platform maps the realm roles.
 */
@Path("/api/counterparties")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CounterpartyResource {

    @Inject
    CounterpartyRegistryBean registry;

    @GET
    @Path("/{code}")
    @RolesAllowed({ "registry-reader", "registry-writer" })
    public Counterparty find(@PathParam("code") String code) {
        return registry.find(code).orElseThrow(NotFoundException::new);
    }

    /** 201 on a new registration, 200 when the Idempotency-Key was seen before (the retry case), 409 on a duplicate code. */
    @POST
    @RolesAllowed("registry-writer")
    public Response register(@HeaderParam("Idempotency-Key") @NotBlank String idempotencyKey, @Valid RegisterCounterparty request) {
        CounterpartyRegistryBean.Registration registration = registry.register(idempotencyKey, request.code(), request.name(), request.country());
        return Response.status(registration.created() ? 201 : 200).entity(registration.counterparty()).build();
    }

    @ServerExceptionMapper
    public Response duplicate(DuplicateCounterpartyException e) {
        return Response.status(409).entity(Map.of("status", 409, "detail", e.getMessage())).build();
    }
}
