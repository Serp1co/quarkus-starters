package it.bancaditalia.quarkus.poc.jakarta.api;

import it.bancaditalia.quarkus.poc.jakarta.api.dto.AccountResponse;
import it.bancaditalia.quarkus.poc.jakarta.api.dto.NewAccountRequest;
import it.bancaditalia.quarkus.poc.jakarta.api.dto.TransferResponse;
import it.bancaditalia.quarkus.poc.jakarta.config.LedgerConfig;
import it.bancaditalia.quarkus.poc.jakarta.domain.Account;
import it.bancaditalia.quarkus.poc.jakarta.service.AccountService;
import it.bancaditalia.quarkus.poc.jakarta.service.TransferService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;

/** Standard JAX-RS, byte for byte what runs on EAP. Quarkus REST executes it without RESTEasy-specific changes. */
@Path("/api/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountResource {

    @Inject
    AccountService accounts;

    @Inject
    TransferService transfers;

    @Inject
    LedgerConfig ledger;

    @GET
    public List<AccountResponse> list() {
        return accounts.list().stream().map(a -> AccountResponse.of(a, ledger.currency())).toList();
    }

    @GET
    @Path("/{iban}")
    public AccountResponse get(@PathParam("iban") String iban) {
        return AccountResponse.of(accounts.get(iban), ledger.currency());
    }

    @POST
    public Response open(@Valid NewAccountRequest request, @Context UriInfo uriInfo) {
        Account account = accounts.open(request.iban(), request.holder(), request.initialBalance());
        return Response.created(uriInfo.getAbsolutePathBuilder().path(account.getIban()).build())
                .entity(AccountResponse.of(account, ledger.currency()))
                .build();
    }

    @GET
    @Path("/{iban}/transfers")
    public List<TransferResponse> transfers(@PathParam("iban") String iban) {
        accounts.get(iban); // 404 for an unknown account rather than an empty list
        return transfers.forAccount(iban).stream().map(t -> TransferResponse.of(t, ledger.currency())).toList();
    }
}
