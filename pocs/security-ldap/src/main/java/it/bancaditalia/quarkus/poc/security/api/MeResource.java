package it.bancaditalia.quarkus.poc.security.api;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Who the platform says I am: the login name, and the roles (AD groups plus the application roles mapped from them). */
@Path("/api/me")
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject
    SecurityIdentity identity;

    @GET
    @Authenticated
    public Map<String, Object> me() {
        return Map.of(
                "user", identity.getPrincipal().getName(),
                "roles", List.copyOf(new TreeSet<>(identity.getRoles())),
                "admin", identity.hasRole("admin"));
    }
}
