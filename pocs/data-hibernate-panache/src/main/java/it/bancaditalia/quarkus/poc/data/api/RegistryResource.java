package it.bancaditalia.quarkus.poc.data.api;

import it.bancaditalia.quarkus.poc.data.api.dto.RegistryOverview;
import it.bancaditalia.quarkus.poc.data.api.dto.TypeResponse;
import it.bancaditalia.quarkus.poc.data.service.RegistryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/api/registry")
@Produces(MediaType.APPLICATION_JSON)
public class RegistryResource {

    @Inject
    RegistryService registry;

    @GET
    public RegistryOverview overview() {
        return new RegistryOverview(registry.supervisor(), registry.countByStatus());
    }

    @GET
    @Path("/types")
    public List<TypeResponse> types() {
        return registry.types().stream().map(TypeResponse::of).toList();
    }

    /** Bulk update in one statement: what a supervisory measure on a whole category looks like. */
    @POST
    @Path("/types/{code}/suspend")
    public Map<String, Integer> suspendType(@PathParam("code") String code) {
        return Map.of("suspended", registry.suspendAllOfType(code));
    }
}
