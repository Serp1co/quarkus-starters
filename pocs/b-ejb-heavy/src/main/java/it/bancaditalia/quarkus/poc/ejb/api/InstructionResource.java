package it.bancaditalia.quarkus.poc.ejb.api;

import it.bancaditalia.quarkus.poc.ejb.api.dto.InstructionResponse;
import it.bancaditalia.quarkus.poc.ejb.api.dto.NewInstructionRequest;
import it.bancaditalia.quarkus.poc.ejb.domain.Instruction;
import it.bancaditalia.quarkus.poc.ejb.service.InstructionService;
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
import java.util.List;

@Path("/api/instructions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InstructionResource {

    @Inject
    InstructionService instructions;

    @POST
    public Response submit(@Valid NewInstructionRequest request, @Context UriInfo uriInfo) {
        Instruction instruction = instructions.submit(request.reference(), request.amount());
        return Response.created(uriInfo.getAbsolutePathBuilder().path(String.valueOf(instruction.getId())).build())
                .entity(InstructionResponse.of(instruction))
                .build();
    }

    @GET
    public List<InstructionResponse> list() {
        return instructions.list().stream().map(InstructionResponse::of).toList();
    }

    @GET
    @Path("/{id}")
    public InstructionResponse get(@PathParam("id") long id) {
        return instructions.find(id).map(InstructionResponse::of)
                .orElseThrow(() -> new NotFoundException("No instruction " + id));
    }
}
