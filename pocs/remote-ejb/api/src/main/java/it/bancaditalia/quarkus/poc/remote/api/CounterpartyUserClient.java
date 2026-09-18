package it.bancaditalia.quarkus.poc.remote.api;

import io.quarkus.oidc.token.propagation.common.AccessToken;
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
 * The registry as the user calls it through the application: the caller's bearer token is propagated
 * ({@code @AccessToken}), so the provider authorizes the user, not the application. On EAP: the remote EJB proxy
 * with the caller's identity propagated by the EJB client. The base URL is {@code quarkus.rest-client.counterparty.url},
 * a platform key.
 */
@RegisterRestClient(configKey = "counterparty")
@AccessToken
@Path("/api/counterparties")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface CounterpartyUserClient {

    @GET
    @Path("/{code}")
    Counterparty find(@PathParam("code") String code);

    @POST
    Counterparty register(@HeaderParam("Idempotency-Key") String idempotencyKey, RegisterCounterparty request);
}
