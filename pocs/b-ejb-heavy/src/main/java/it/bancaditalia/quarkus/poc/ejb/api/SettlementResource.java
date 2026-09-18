package it.bancaditalia.quarkus.poc.ejb.api;

import it.bancaditalia.quarkus.poc.ejb.api.dto.SettlementRunResponse;
import it.bancaditalia.quarkus.poc.ejb.reference.ReferenceRates;
import it.bancaditalia.quarkus.poc.ejb.repository.SettlementRunRepository;
import it.bancaditalia.quarkus.poc.ejb.service.FailureNotifier;
import it.bancaditalia.quarkus.poc.ejb.service.SettlementService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Path("/api/settlement")
@Produces(MediaType.APPLICATION_JSON)
public class SettlementResource {

    @Inject
    SettlementService settlement;

    @Inject
    SettlementRunRepository runs;

    @Inject
    FailureNotifier notifier;

    @Inject
    ReferenceRates rates;

    /** Runs the settlement now, outside the timer (an operator's "run the batch" button). */
    @POST
    @Path("/runs")
    public SettlementRunResponse runNow() {
        return SettlementRunResponse.of(settlement.runNow());
    }

    @GET
    @Path("/runs")
    public List<SettlementRunResponse> runs() {
        return settlement.runs().stream().map(SettlementRunResponse::of).toList();
    }

    @GET
    @Path("/notifications")
    public List<String> notifications() {
        return notifier.sent();
    }

    /** The Quartz cluster as the database sees it: one row per live instance, and the persisted jobs. */
    @GET
    @Path("/cluster")
    public Map<String, Object> cluster() {
        return Map.of("instances", runs.quartzInstances(), "jobs", runs.quartzJobs());
    }

    @GET
    @Path("/rates/{currency}")
    public Map<String, Object> rate(@PathParam("currency") String currency) {
        try {
            BigDecimal rate = rates.rate(currency.toUpperCase());
            return Map.of("currency", currency.toUpperCase(), "rate", rate, "loadedAt", rates.loadedAt());
        } catch (NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        }
    }
}
