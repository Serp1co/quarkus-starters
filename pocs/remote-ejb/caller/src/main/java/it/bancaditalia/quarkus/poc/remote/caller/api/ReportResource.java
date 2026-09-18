package it.bancaditalia.quarkus.poc.remote.caller.api;

import io.grpc.StatusRuntimeException;
import it.bancaditalia.quarkus.poc.remote.api.grpc.CounterpartyReply;
import it.bancaditalia.quarkus.poc.remote.caller.config.ReportsConfig;
import it.bancaditalia.quarkus.poc.remote.caller.domain.Report;
import it.bancaditalia.quarkus.poc.remote.caller.service.ReportService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Path("/api/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportResource {

    public record FileReport(@NotBlank String counterpartyCode, @NotNull @DecimalMin("0.01") BigDecimal amount,
            String counterpartyName, String country) {
    }

    public record Retry(String counterpartyName, String country) {
    }

    @Inject
    ReportService reports;

    @Inject
    ReportsConfig config;

    @POST
    @RolesAllowed("reporter")
    public Response file(@Valid FileReport request) {
        Report report = reports.file(request.counterpartyCode(), request.amount(), request.counterpartyName(), request.country());
        return Response.status(201).entity(report).build();
    }

    @GET
    @RolesAllowed("reporter")
    public List<Report> all() {
        return reports.all();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("reporter")
    public Report get(@PathParam("id") String id) {
        return reports.get(id).orElseThrow(NotFoundException::new);
    }

    @POST
    @Path("/{id}/retry")
    @RolesAllowed("reporter")
    public Report retry(@PathParam("id") String id, Retry retry) {
        return reports.retry(id, retry == null ? null : retry.counterpartyName(), retry == null ? null : retry.country())
                .orElseThrow(NotFoundException::new);
    }

    /** The gRPC variation of the lookup: the typed contract, the user's token propagated. */
    @GET
    @Path("/{id}/verify")
    @RolesAllowed("reporter")
    public Response verify(@PathParam("id") String id) {
        try {
            CounterpartyReply reply = reports.verify(id);
            if (reply == null) {
                throw new NotFoundException();
            }
            return Response.ok(Map.of("code", reply.getCode(), "name", reply.getName(), "country", reply.getCountry(),
                    "status", reply.getStatus(), "via", "grpc")).build();
        } catch (StatusRuntimeException e) {
            return Response.status(502).entity(Map.of("status", 502, "detail", "registry (gRPC): " + e.getStatus())).build();
        }
    }

    @GET
    @Path("/desk")
    @RolesAllowed("reporter")
    public Map<String, String> desk() {
        return Map.of("unit", config.unit());
    }
}
