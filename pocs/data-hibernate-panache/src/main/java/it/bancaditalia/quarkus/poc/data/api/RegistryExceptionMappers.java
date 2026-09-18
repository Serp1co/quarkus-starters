package it.bancaditalia.quarkus.poc.data.api;

import it.bancaditalia.quarkus.poc.data.service.NotRegisteredException;
import it.bancaditalia.quarkus.poc.data.service.RegistryException;
import it.bancaditalia.quarkus.poc.data.service.StaleVersionException;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class RegistryExceptionMappers {

    @ServerExceptionMapper
    public Response notRegistered(NotRegisteredException e) {
        return problem(404, e.getMessage());
    }

    @ServerExceptionMapper
    public Response refused(RegistryException e) {
        return problem(422, e.getMessage());
    }

    @ServerExceptionMapper
    public Response stale(StaleVersionException e) {
        return problem(409, e.getMessage());
    }

    /** The race Hibernate catches at flush: same answer as the explicit version check. */
    @ServerExceptionMapper
    public Response optimisticLock(OptimisticLockException e) {
        return problem(409, "The row changed since it was read; reload and retry");
    }

    private static Response problem(int status, String detail) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("status", status, "detail", detail)).build();
    }
}
