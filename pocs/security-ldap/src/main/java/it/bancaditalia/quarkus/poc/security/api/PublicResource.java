package it.bancaditalia.quarkus.poc.security.api;

import it.bancaditalia.quarkus.poc.security.config.DeskConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

/** Open by the platform's path policy (/api/public/*): what an applicant sees before logging in. */
@Path("/api/public")
@Produces(MediaType.APPLICATION_JSON)
public class PublicResource {

    @Inject
    DeskConfig config;

    @GET
    @Path("/status")
    public Map<String, String> status() {
        return Map.of("desk", config.unit(), "status", "open");
    }
}
