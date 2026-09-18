package it.bancaditalia.apps.sample;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/api/hello")
@Produces(MediaType.APPLICATION_JSON)
public class SampleResource {

    @Inject
    SampleConfig config;

    @GET
    public Map<String, String> hello() {
        return Map.of("greeting", config.greeting());
    }
}
