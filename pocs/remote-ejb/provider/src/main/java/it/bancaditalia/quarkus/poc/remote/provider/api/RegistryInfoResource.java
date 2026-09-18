package it.bancaditalia.quarkus.poc.remote.provider.api;

import it.bancaditalia.quarkus.poc.remote.provider.config.RegistryConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/api/public/registry")
@Produces(MediaType.APPLICATION_JSON)
public class RegistryInfoResource {

    @Inject
    RegistryConfig config;

    @GET
    public Map<String, String> info() {
        return Map.of("unit", config.unit());
    }
}
