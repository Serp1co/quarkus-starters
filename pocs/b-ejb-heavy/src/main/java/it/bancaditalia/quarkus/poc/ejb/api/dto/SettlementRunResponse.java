package it.bancaditalia.quarkus.poc.ejb.api.dto;

import it.bancaditalia.quarkus.poc.ejb.domain.SettlementRun;
import java.time.Instant;

public record SettlementRunResponse(long id, String node, Instant startedAt, Instant finishedAt, int settled, int rejected,
        int failedBatches) {

    public static SettlementRunResponse of(SettlementRun r) {
        return new SettlementRunResponse(r.getId(), r.getNode(), r.getStartedAt(), r.getFinishedAt(), r.getSettled(),
                r.getRejected(), r.getFailed());
    }
}
