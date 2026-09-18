package it.bancaditalia.quarkus.poc.remote.api;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * The registry as the application calls it as itself: a token from the application's own RHBK client (client
 * credentials, {@code @OidcClientFilter}), for what runs without a user (batches, timers, retries). On EAP: the
 * run-as principal of a batch EJB. Same URL key as the user client: one provider, two identities.
 */
@RegisterRestClient(configKey = "counterparty")
@OidcClientFilter
@Path("/api/counterparties")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface CounterpartyServiceClient {

    @GET
    @Path("/{code}")
    Counterparty find(@PathParam("code") String code);

    @POST
    Counterparty register(@HeaderParam("Idempotency-Key") String idempotencyKey, RegisterCounterparty request);
}
