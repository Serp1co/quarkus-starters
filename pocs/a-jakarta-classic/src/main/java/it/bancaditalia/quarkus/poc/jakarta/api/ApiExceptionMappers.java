package it.bancaditalia.quarkus.poc.jakarta.api;

import it.bancaditalia.quarkus.poc.jakarta.domain.AccountAlreadyExistsException;
import it.bancaditalia.quarkus.poc.jakarta.domain.AccountNotFoundException;
import it.bancaditalia.quarkus.poc.jakarta.domain.InsufficientFundsException;
import it.bancaditalia.quarkus.poc.jakarta.domain.TransferRejectedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Domain exceptions to HTTP statuses with standard JAX-RS {@link ExceptionMapper}s, unchanged from EAP.
 * Bean Validation failures need no mapper: Quarkus answers 400 with the list of violations.
 */
public final class ApiExceptionMappers {

    private ApiExceptionMappers() {
    }

    public record ErrorResponse(int status, String error, String message) {
    }

    /** 422 has no constant in Jakarta REST 3.1, hence the explicit status code and reason. */
    private static final int UNPROCESSABLE_ENTITY = 422;

    private static Response error(Response.Status status, RuntimeException e) {
        return error(status.getStatusCode(), status.getReasonPhrase(), e);
    }

    private static Response error(int status, String reason, RuntimeException e) {
        return Response.status(status, reason)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(status, reason, e.getMessage()))
                .build();
    }

    @Provider
    public static class AccountNotFound implements ExceptionMapper<AccountNotFoundException> {
        @Override
        public Response toResponse(AccountNotFoundException e) {
            return error(Response.Status.NOT_FOUND, e);
        }
    }

    @Provider
    public static class AccountAlreadyExists implements ExceptionMapper<AccountAlreadyExistsException> {
        @Override
        public Response toResponse(AccountAlreadyExistsException e) {
            return error(Response.Status.CONFLICT, e);
        }
    }

    @Provider
    public static class InsufficientFunds implements ExceptionMapper<InsufficientFundsException> {
        @Override
        public Response toResponse(InsufficientFundsException e) {
            return error(UNPROCESSABLE_ENTITY, "Unprocessable Entity", e);
        }
    }

    @Provider
    public static class TransferRejected implements ExceptionMapper<TransferRejectedException> {
        @Override
        public Response toResponse(TransferRejectedException e) {
            return error(UNPROCESSABLE_ENTITY, "Unprocessable Entity", e);
        }
    }
}
