package it.bancaditalia.quarkus.poc.security.api;

import io.quarkus.security.identity.SecurityIdentity;
import it.bancaditalia.quarkus.poc.security.config.DeskConfig;
import it.bancaditalia.quarkus.poc.security.domain.AuthorizationRequest;
import it.bancaditalia.quarkus.poc.security.domain.RequestStore;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * The authorization desk. The path policy of bdi-config-security already requires an identity under /api;
 * {@code @RolesAllowed} is the fine-grained decision that stays in the code, exactly as on EAP. The roles are
 * application roles (reader, operator, admin): which AD group grants which of them is the platform's per
 * environment, through {@code quarkus.http.auth.roles-mapping}.
 */
@Path("/api/requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeskResource {

    public record FileRequest(@NotBlank @Size(max = 200) String applicant, @NotBlank @Size(max = 500) String subject) {
    }

    public record Decision(@NotNull AuthorizationRequest.Status decision, @Size(max = 500) String note) {
    }

    @Inject
    RequestStore store;

    @Inject
    SecurityIdentity identity;

    @Inject
    DeskConfig config;

    @GET
    @RolesAllowed({ "reader", "operator", "admin" })
    public List<AuthorizationRequest> list() {
        return store.all();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({ "reader", "operator", "admin" })
    public AuthorizationRequest get(@PathParam("id") long id) {
        return store.find(id).orElseThrow(NotFoundException::new);
    }

    @POST
    @RolesAllowed({ "operator", "admin" })
    public Response file(@Valid FileRequest request) {
        try {
            AuthorizationRequest filed = store.file(request.applicant(), request.subject(), identity.getPrincipal().getName());
            return Response.status(201).entity(filed).build();
        } catch (IllegalStateException e) {
            return Response.status(422).entity(Map.of("status", 422, "detail", e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/decision")
    @RolesAllowed("admin")
    public AuthorizationRequest decide(@PathParam("id") long id, @Valid Decision decision) {
        if (decision.decision() == AuthorizationRequest.Status.PENDING) {
            throw new jakarta.ws.rs.BadRequestException("a decision is APPROVED or REJECTED");
        }
        return store.decide(id, decision.decision(), identity.getPrincipal().getName(), decision.note())
                .orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/desk")
    @RolesAllowed({ "reader", "operator", "admin" })
    public Map<String, Object> desk() {
        return Map.of("unit", config.unit(), "maxPending", config.maxPending());
    }
}
