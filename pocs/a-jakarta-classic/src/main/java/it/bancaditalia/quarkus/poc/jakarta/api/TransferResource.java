package it.bancaditalia.quarkus.poc.jakarta.api;

import it.bancaditalia.quarkus.poc.jakarta.api.dto.TransferRequest;
import it.bancaditalia.quarkus.poc.jakarta.api.dto.TransferResponse;
import it.bancaditalia.quarkus.poc.jakarta.config.LedgerConfig;
import it.bancaditalia.quarkus.poc.jakarta.domain.Transfer;
import it.bancaditalia.quarkus.poc.jakarta.service.TransferService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/api/transfers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TransferResource {

    @Inject
    TransferService transfers;

    @Inject
    LedgerConfig ledger;

    @POST
    public Response execute(@Valid TransferRequest request, @Context UriInfo uriInfo) {
        Transfer transfer = transfers.execute(request.debtorIban(), request.creditorIban(), request.amount(),
                request.reference());
        return Response.created(uriInfo.getAbsolutePathBuilder().path(String.valueOf(transfer.getId())).build())
                .entity(TransferResponse.of(transfer, ledger.currency()))
                .build();
    }

    @GET
    @Path("/{id}")
    public TransferResponse get(@PathParam("id") long id) {
        return transfers.find(id)
                .map(t -> TransferResponse.of(t, ledger.currency()))
                .orElseThrow(() -> new NotFoundException("No transfer with id " + id));
    }
}
