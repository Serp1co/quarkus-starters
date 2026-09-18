package it.bancaditalia.quarkus.poc.data.api;

import it.bancaditalia.quarkus.poc.data.api.dto.BranchRequest;
import it.bancaditalia.quarkus.poc.data.api.dto.BranchResponse;
import it.bancaditalia.quarkus.poc.data.api.dto.IntermediaryResponse;
import it.bancaditalia.quarkus.poc.data.api.dto.RegisterIntermediaryRequest;
import it.bancaditalia.quarkus.poc.data.api.dto.StatusChangeRequest;
import it.bancaditalia.quarkus.poc.data.audit.HistoryService;
import it.bancaditalia.quarkus.poc.data.domain.Intermediary;
import it.bancaditalia.quarkus.poc.data.query.IntermediarySearch;
import it.bancaditalia.quarkus.poc.data.query.IntermediarySummary;
import it.bancaditalia.quarkus.poc.data.query.PageResult;
import it.bancaditalia.quarkus.poc.data.query.ProvinceCount;
import it.bancaditalia.quarkus.poc.data.service.RegistryService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Set;

@Path("/api/intermediaries")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IntermediaryResource {

    private static final Set<String> SORTABLE = Set.of("abi", "name", "status", "registeredAt");

    @Inject
    RegistryService registry;

    @Inject
    HistoryService history;

    @POST
    public Response register(@Valid RegisterIntermediaryRequest request, @Context UriInfo uriInfo) {
        Intermediary created = registry.register(request.abi(), request.name(), request.type(),
                request.headquarters().toDomain());
        return Response.created(uriInfo.getAbsolutePathBuilder().path(created.getAbi()).build())
                .entity(IntermediaryResponse.of(created)).build();
    }

    /** Paged search: the page is the body, the total is a header, the criteria are query parameters. */
    @GET
    public Response search(@QueryParam("type") String type, @QueryParam("status") Intermediary.Status status,
            @QueryParam("q") String nameContains, @QueryParam("province") String province,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(1000) int size,
            @QueryParam("sort") @DefaultValue("abi") String sort,
            @QueryParam("dir") @DefaultValue("asc") String direction) {
        if (!SORTABLE.contains(sort)) {
            return Response.status(400).entity("sort must be one of " + SORTABLE).build();
        }
        PageResult<IntermediarySummary> result = registry.search(
                new IntermediarySearch(type, status, nameContains, province), page, size, sort, !"desc".equals(direction));
        return Response.ok(result.items())
                .header("X-Total-Count", result.total())
                .header("X-Page", result.page())
                .header("X-Page-Size", result.size())
                .build();
    }

    @GET
    @Path("/{abi}")
    public IntermediaryResponse get(@PathParam("abi") String abi) {
        return IntermediaryResponse.of(registry.get(abi));
    }

    @PUT
    @Path("/{abi}/status")
    public IntermediaryResponse changeStatus(@PathParam("abi") String abi, @Valid StatusChangeRequest request) {
        return IntermediaryResponse.of(registry.changeStatus(abi, request.status(), request.version()));
    }

    @GET
    @Path("/{abi}/history")
    public List<HistoryService.Revision> history(@PathParam("abi") String abi) {
        registry.get(abi); // 404 before an empty history
        return history.of(abi);
    }

    @POST
    @Path("/{abi}/branches")
    public Response addBranch(@PathParam("abi") String abi, @Valid BranchRequest request) {
        return Response.status(201).entity(BranchResponse.of(registry.addBranch(abi, request.code(), request.address().toDomain()))).build();
    }

    @GET
    @Path("/{abi}/branches")
    public List<BranchResponse> branches(@PathParam("abi") String abi) {
        return registry.branches(abi).stream().map(BranchResponse::of).toList();
    }

    @GET
    @Path("/{abi}/branches/by-province")
    public List<ProvinceCount> branchesPerProvince(@PathParam("abi") String abi) {
        return registry.branchesPerProvince(abi);
    }
}
